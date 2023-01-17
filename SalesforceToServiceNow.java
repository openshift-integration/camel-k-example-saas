// camel-k: language=java
// camel-k: config=secret:secret-saas
// camel-k: dependency=camel:servicenow
// camel-k: dependency=mvn:javax.ws.rs:javax.ws.rs-api:2.1

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.servicenow.ServiceNowConstants;
import org.apache.camel.model.dataformat.JsonLibrary;

public class SalesforceToServiceNow extends RouteBuilder {

  private static final String SOBJECT_FIELDS = "Id,Account.Name,CaseNumber,Subject,Description";

  @Override
  public void configure() throws Exception {

    // main route
    from("salesforce:CamelCaseTopic?"
        + "notifyForOperationCreate=true&notifyForOperationUpdate=true&notifyForOperationDelete=false&notifyForOperationUndelete=false"
        + "&updateTopic=true&rawPayload=true&sObjectQuery=SELECT Id, CaseNumber FROM Case")

            .to("direct:enrichCase")
            .to("direct:mapCaseToIncident")
            .to("direct:createIncident")
            .to("direct:mapIncidentToCase")
            .to("direct:updateCase");

    // sub-routes

    from("direct:enrichCase")
        .unmarshal().json(JsonLibrary.Jackson)
        .setHeader(SalesforceEndpointConfig.SOBJECT_ID, simple("${body[Id]}"))
        .to("salesforce:getSObject?rawPayload=true&sObjectName=Case&sObjectFields=" + SOBJECT_FIELDS)
        .unmarshal().json(JsonLibrary.Jackson)
        .log("New Case: ID: ${body[Id]}, Account.Name: ${body[Account][Name]}, CaseNumber: ${body[CaseNumber]}, Subject: ${body[Subject]}");

    from("direct:mapCaseToIncident")
        .setBody(simple("{\"correlation_id\": \"${body[Id]}\", "
          + "\"short_description\": \"${body[Subject]}\", "
          + "\"description\": \"Salesforce Case [${body[CaseNumber]}] for Account [${body[Account][Name]}] : ${body[Description]}\"}"));

    from("direct:createIncident")
        .setHeader(ServiceNowConstants.ACTION, constant(ServiceNowConstants.ACTION_CREATE))
        .to("servicenow:{{camel.component.servicenow.instanceName}}?resource=table&table=incident&model.incident=java.lang.String")
        .unmarshal().json(JsonLibrary.Jackson)
        .setHeader("incidentNumber", simple("${body[number]}"))
        .log("Created Incident with number ${headers.incidentNumber}");

    from("direct:mapIncidentToCase")
        .setBody(simple("{\"EngineeringReqNumber__c\": \"${headers.incidentNumber}\"}"));

    from("direct:updateCase")
        .to("salesforce:updateSObject?sObjectName=Case")
        .log("Updated Case with Incident number ${headers.incidentNumber}");

  };

}
