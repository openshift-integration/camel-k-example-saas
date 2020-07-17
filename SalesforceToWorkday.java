// camel-k: language=java

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.PropertyInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CxfPayload;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.support.builder.Namespaces;

import org.apache.camel.component.cxf.CxfConfigurer;
import org.apache.camel.component.cxf.ChainedCxfConfigurer;
import org.apache.cxf.common.util.PropertyUtils;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.common.ConfigurationConstants;
import org.apache.wss4j.common.ext.WSPasswordCallback;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class SalesforceToWorkday extends RouteBuilder {

  private static final String ACCOUNT_FIELDS = "Id, Name, Phone, Website";
  private static final String WORKDAY_NS = "urn:com.workday/bsvc";
  private static final String WORKDAY_WSDL_NS = "urn:com.workday/bsvc/Revenue_Management";
  private static final String WORKDAY_WSDL="https://community.workday.com/sites/default/files/file-hosting/productionapi/Revenue_Management/v34.2/Revenue_Management.wsdl";

  @PropertyInject("workday-username")
  private String workdayUsername;
  
  @PropertyInject("workday-password")
  private String workdayPassword;
  
  private final DocumentBuilder builder;
  
  SalesforceToWorkday() throws Exception {
    DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    builderFactory.setNamespaceAware(true);
    builder = builderFactory.newDocumentBuilder();
  }
  
  @Override
  public void configure() throws Exception {

    Namespaces ns = new Namespaces("wd", WORKDAY_NS);

    // Salesforce to Workday route
    from("salesforce:CamelAccountTopic?" +
        "notifyForOperationCreate=true&notifyForOperationUpdate=false&notifyForOperationDelete=false&notifyForOperationUndelete=false" + 
        "&updateTopic=true&rawPayload=true&sObjectQuery=SELECT " + ACCOUNT_FIELDS + " FROM Account")

      .unmarshal().json(JsonLibrary.Jackson)
      .setHeader(SalesforceEndpointConfig.SOBJECT_ID, simple("${body[Id]}"))
      .log("New Account ${headers." + SalesforceEndpointConfig.SOBJECT_ID + "}")
      .bean(this, "toWorkdayRequest")

      // replace the address with a real Workday URL when not using the mock service
      .to("cxf:http://localhost:8080/services/workday?dataFormat=PAYLOAD" +
        "&defaultOperationName=Put_Customer_Request&defaultOperationNamespace=" + WORKDAY_WSDL_NS +
        "&loggingFeatureEnabled=true&loggingSizeLimit=10000" +
        "&cxfConfigurer=#wssecClientConfigurer&wsdlURL=" + WORKDAY_WSDL)

      .setBody(simple("${body.getBody().get(0)}"))
      .setBody(xpath("/wd:Put_Customer_Request_Response/wd:Customer_Request_Reference/wd:ID/text()", ns))
      .setHeader("customerId", simple("${body.item(0).getTextContent()}"))
      .log("Created Customer with id ${headers.customerId}")

      .setBody(simple("{\"AccountNumber\": \"${headers.customerId}\"}"))
      .to("salesforce:updateSObject?sObjectName=Account")
      .log("Updated Account with Customer id ${headers.customerId}");

    // Mock Workday Server route
    // NOTE: comment this route when using a real Workday instance
    from("cxf:cxf?address=http://0.0.0.0:8080/services/workday" +
      "&cxfConfigurer=#wssecServerConfigurer&dataFormat=PAYLOAD&wsdlURL=" + WORKDAY_WSDL)
      .bean(this, "toWorkdayResponse");
  }

  // map Salesforce Account to Workday Customer request
  public Document toWorkdayRequest(Map sfRecord) {

    final Document doc = builder.newDocument();
    Element request = doc.createElementNS(WORKDAY_NS, "wd:Put_Customer_Request_Request");
    doc.appendChild(request);

    Element customerRequest = doc.createElementNS(WORKDAY_NS, "wd:Put_Customer_Request");
    request.appendChild(customerRequest);
    Element customerRequestData = doc.createElement("wd:Customer_Request_Data");
    customerRequest.appendChild(customerRequestData);

    Element requestName = doc.createElement("wd:Customer_Request_Name");
    requestName.setTextContent(String.valueOf(sfRecord.get("Name")));
    customerRequestData.appendChild(requestName);
    
    Element contactData = doc.createElement("wd:Contact_Data");
    customerRequestData.appendChild(contactData);

    Element phoneData = doc.createElement("wd:Phone_Data");
    phoneData.setTextContent(String.valueOf(sfRecord.get("Phone")));
    contactData.appendChild(phoneData);
    
    Element webAddressData = doc.createElement("wd:Web_Address_Data");
    webAddressData.setTextContent(String.valueOf(sfRecord.get("Website")));
    contactData.appendChild(webAddressData);

    return doc;
  }

  @BindToRegistry("wssecClientConfigurer")
  public CxfConfigurer getClientCxfConfigurer() {
    return new ChainedCxfConfigurer.NullCxfConfigurer() {

      @Override
      public void configureClient(Client client) {
          client.getEndpoint().getOutInterceptors().add(new WSS4JOutInterceptor(getWSSEProperties()));
      }
    };
  }
  
  @BindToRegistry("wssecServerConfigurer")
  public CxfConfigurer getServerCxfConfigurer() {
    return new ChainedCxfConfigurer.NullCxfConfigurer() {
  
      @Override
      public void configureServer(Server server) {
          server.getEndpoint().getInInterceptors().add(new WSS4JInInterceptor(getWSSEProperties()));
      }
    };
  }

  private Map<String, Object> getWSSEProperties() {
    final Map<String, Object> outProps = new HashMap<>();
    final StringBuilder actions = new StringBuilder();

      outProps.put(ConfigurationConstants.USER, workdayUsername);
      outProps.put(ConfigurationConstants.PW_CALLBACK_REF, (CallbackHandler) callbacks -> {
          for (Callback callback : callbacks) {
              final WSPasswordCallback passwordCallback = (WSPasswordCallback) callback;
              if (workdayUsername.equals(passwordCallback.getIdentifier())) {
                  passwordCallback.setPassword(workdayPassword);
                  return;
              }
          }
      });

      outProps.put(ConfigurationConstants.PASSWORD_TYPE, "PasswordText");
      actions.append(ConfigurationConstants.USERNAME_TOKEN);
      actions.append(' ');
      actions.append(ConfigurationConstants.TIMESTAMP);

      outProps.put(ConfigurationConstants.ADD_USERNAMETOKEN_NONCE, "true");
      outProps.put(ConfigurationConstants.ADD_USERNAMETOKEN_CREATED, "true");

      outProps.put(ConfigurationConstants.ACTION, actions.toString());

      return outProps;
  }

  // response from mock Workday service
  // public Document toWorkdayResponse(Element request) {
  public Document toWorkdayResponse(CxfPayload request) {
    final Document doc = builder.newDocument();
    Element response = doc.createElementNS(WORKDAY_NS, "wd:Put_Customer_Request_Response");
    doc.appendChild(response);

    Element customerReference = doc.createElementNS(WORKDAY_NS, "wd:Customer_Request_Reference");
    response.appendChild(customerReference);

    Element customerId = doc.createElement("wd:ID");
    customerId.setTextContent(UUID.randomUUID().toString());
    customerReference.appendChild(customerId);

    return doc;
  }

}
