// camel-k: language=java
// camel-k: profile=openshift
// camel-k: secret=secret-saas
// camel-k: source=customizers/WSSecurityCustomizer.java
// camel-k: dependency=camel:jetty
// camel-k: dependency=mvn:org.apache.cxf:cxf-rt-transports-http-jetty:3.3.6.fuse-jdk11-800019-redhat-00001
// camel-k: dependency=mvn:org.apache.cxf:cxf-rt-ws-security:3.3.6.fuse-jdk11-800019-redhat-00001
// camel-k: dependency=mvn:org.apache.wss4j:wss4j-ws-security-common:2.2.2

import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.support.builder.Namespaces;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

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

    from("salesforce:CamelAccountTopic?"
        + "notifyForOperationCreate=true&notifyForOperationUpdate=false&notifyForOperationDelete=false&notifyForOperationUndelete=false"
        + "&updateTopic=true&rawPayload=true&sObjectQuery=SELECT " + ACCOUNT_FIELDS + " FROM Account")

            .unmarshal().json(JsonLibrary.Jackson)
            .setHeader(SalesforceEndpointConfig.SOBJECT_ID, simple("${body[Id]}"))
            .log("New Account created: ID: ${headers." + SalesforceEndpointConfig.SOBJECT_ID + "}, Name: ${body[Name]}, Phone: ${body[Phone]}, Website: ${body[Website]}")
            .bean(this, "toWorkdayRequest")

            .to("cxf:{{workday-url}}?dataFormat=PAYLOAD"
                + "&defaultOperationName=Put_Customer_Request&defaultOperationNamespace=" + WORKDAY_WSDL_NS
                + "&loggingFeatureEnabled=true&loggingSizeLimit=10000"
                + "&cxfConfigurer=#wssecClientConfigurer&wsdlURL=" + WORKDAY_WSDL)

            .setBody(simple("${body.getBody().get(0)}")) // get's the first request XML body from SOAP response
            .setBody(xpath("/wd:Put_Customer_Request_Response/wd:Customer_Request_Reference/wd:ID/text()", ns)) // gets the Request Reference ID as NodeList
            .setHeader("customerId", simple("${body.item(0).getTextContent()}")) // save ID to header `customerId`
            .log("Created Customer with id ${headers.customerId}")

            .setBody(simple("{\"AccountNumber\": \"${headers.customerId}\"}")) // create a simple Salesforce JSON request body
            .to("salesforce:updateSObject?sObjectName=Account")
            .log("Updated Account with Customer id ${headers.customerId}");
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

}