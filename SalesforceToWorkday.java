// camel-k: language=java
// camel-k: dependency=camel:jetty
// camel-k: dependency=mvn:org.apache.cxf:cxf-rt-transports-http-jetty:3.3.6.fuse-jdk11-800019-redhat-00001
// camel-k: dependency=mvn:org.apache.cxf:cxf-rt-ws-security:3.3.6.fuse-jdk11-800019-redhat-00001
// camel-k: dependency=mvn:org.apache.wss4j:wss4j-ws-security-common:2.2.2
// camel-k: source=customizers/WSSecurityCustomizer.java

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CxfPayload;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.support.builder.Namespaces;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class SalesforceToWorkday extends RouteBuilder {

  private static final String ACCOUNT_FIELDS = "Id, Name, Phone, Website";
  private static final String WORKDAY_NS = "urn:com.workday/bsvc";
  private static final String WORKDAY_WSDL_NS = "urn:com.workday/bsvc/Revenue_Management";
  private static final String WORKDAY_WSDL = "https://community.workday.com/sites/default/files/file-hosting/productionapi/Revenue_Management/v34.2/Revenue_Management.wsdl";

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
    from("salesforce:CamelAccountTopic?"
        + "notifyForOperationCreate=true&notifyForOperationUpdate=false&notifyForOperationDelete=false&notifyForOperationUndelete=false"
        + "&updateTopic=true&rawPayload=true&sObjectQuery=SELECT " + ACCOUNT_FIELDS + " FROM Account")

            .unmarshal().json(JsonLibrary.Jackson).setHeader(SalesforceEndpointConfig.SOBJECT_ID, simple("${body[Id]}"))
            .log("New Account ${headers." + SalesforceEndpointConfig.SOBJECT_ID + "}").bean(this, "toWorkdayRequest")

            // replace the address with a real Workday URL when not using the mock service
            .to("cxf:http://localhost:8080/services/workday?dataFormat=PAYLOAD"
                + "&defaultOperationName=Put_Customer_Request&defaultOperationNamespace=" + WORKDAY_WSDL_NS
                + "&loggingFeatureEnabled=true&loggingSizeLimit=10000"
                + "&cxfConfigurer=#wssecClientConfigurer&wsdlURL=" + WORKDAY_WSDL)

            .setBody(simple("${body.getBody().get(0)}"))
            .setBody(xpath("/wd:Put_Customer_Request_Response/wd:Customer_Request_Reference/wd:ID/text()", ns))
            .setHeader("customerId", simple("${body.item(0).getTextContent()}"))
            .log("Created Customer with id ${headers.customerId}")

            .setBody(simple("{\"AccountNumber\": \"${headers.customerId}\"}"))
            .to("salesforce:updateSObject?sObjectName=Account")
            .log("Updated Account with Customer id ${headers.customerId}");

    // Mock Workday Server route
    // NOTE: comment this route when using a real Workday instance
    from("cxf:cxf?address=http://0.0.0.0:8080/services/workday"
        + "&cxfConfigurer=#wssecServerConfigurer&dataFormat=PAYLOAD&wsdlURL=" + WORKDAY_WSDL).bean(this,
            "toWorkdayResponse");
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