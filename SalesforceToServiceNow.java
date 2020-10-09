// camel-k: language=java
// camel-k: profile=openshift
// camel-k: secret=secret-saas

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.servicenow.ServiceNowConstants;
import org.apache.camel.model.dataformat.JsonLibrary;

public class SalesforceToServiceNow extends RouteBuilder {

  private static final String SOBJECT_FIELDS = "Id,Account.Name,CaseNumber,Subject,Description";

  SalesforceToServiceNow() throws Exception {
  }

  @Override
  public void configure() throws Exception {

    from("salesforce:CamelCaseTopic?"
        + "notifyForOperationCreate=true&notifyForOperationUpdate=true&notifyForOperationDelete=false&notifyForOperationUndelete=false"
        + "&updateTopic=true&rawPayload=true&sObjectQuery=SELECT Id, CaseNumber FROM Case")

            .unmarshal().json(JsonLibrary.Jackson)
            // save Salesforce object ID in header for to("salesforce:updateSObject")
            .setHeader(SalesforceEndpointConfig.SOBJECT_ID, simple("${body[Id]}"))
            // lookup fields for new Case sobject
            .to("salesforce:getSObject?rawPayload=true&sObjectName=Case&sObjectFields=" + SOBJECT_FIELDS)
            .unmarshal().json(JsonLibrary.Jackson)
            .log("New Case created: ID: ${body[Id]}, Account.Name: ${body[Account][Name]}, CaseNumber: ${body[CaseNumber]}, Subject: ${body[Subject]}")

            // create servicenow request json
            .setBody(simple("{\"correlation_id\": \"${body[Id]}\", " +
              "\"short_description\": \"${body[Subject]}\", " +
              "\"description\": \"Salesforce Case [${body[CaseNumber]}] for Account [${body[Account][Name]}] : ${body[Description]}\"}"))

            // create new ServiceNow incident
            .setHeader(ServiceNowConstants.ACTION, constant(ServiceNowConstants.ACTION_CREATE))
            .to("servicenow:{{camel.component.servicenow.instanceName}}?resource=table&table=incident&model.incident=java.lang.String")
            .unmarshal().json(JsonLibrary.Jackson)

            // save sys_id to header `incidentNumber`
            .setHeader("incidentNumber", simple("${body[number]}")) 
            .log("Created Incident with number ${headers.incidentNumber}")

            // create Salesforce request json
            .setBody(simple("{\"EngineeringReqNumber__c\": \"${headers.incidentNumber}\"}")) 

            // update Salesforce Case
            .to("salesforce:updateSObject?sObjectName=Case")
            .log("Updated Case with Incident number ${headers.incidentNumber}");
  }

}
