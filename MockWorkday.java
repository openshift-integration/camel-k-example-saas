// camel-k: language=java
// camel-k: profile=openshift
// camel-k: secret=mock-secret-saas
// camel-k: source=customizers/WSSecurityCustomizer.java
// camel-k: dependency=camel:jetty
// camel-k: dependency=mvn:org.apache.cxf:cxf-rt-transports-http-jetty:3.3.6.fuse-jdk11-800019-redhat-00001
// camel-k: dependency=mvn:org.apache.cxf:cxf-rt-ws-security:3.3.6.fuse-jdk11-800019-redhat-00001
// camel-k: dependency=mvn:org.apache.wss4j:wss4j-ws-security-common:2.2.2

 import java.util.UUID;

import org.apache.camel.builder.RouteBuilder;

public class MockWorkday extends RouteBuilder {

  private static final String WORKDAY_WSDL = "https://community.workday.com/sites/default/files/file-hosting/productionapi/Revenue_Management/v34.2/Revenue_Management.wsdl";

  @Override
  public void configure() throws Exception {

    from("cxf:cxf?address=http://0.0.0.0:8080/services/workday"
        + "&cxfConfigurer=#wssecServerConfigurer&dataFormat=PAYLOAD&wsdlURL=" + WORKDAY_WSDL)
        .log("Received Workday request: ${body}")
        .bean(UUID.class, "randomUUID")
        .setBody(simple("<wd:Put_Customer_Request_Response xmlns:wd=\"urn:com.workday/bsvc\"><wd:Customer_Request_Reference><wd:ID>${body.toString()}</wd:ID></wd:Customer_Request_Reference></wd:Put_Customer_Request_Response>"));
  }

}