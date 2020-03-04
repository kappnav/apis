/*
 * Copyright 2020 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package application.rest.v1;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import java.util.Date;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import javax.inject.Inject;
import javax.validation.constraints.Pattern;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import application.rest.v1.actions.CommandLineTokenizer;
import application.rest.v1.actions.ResolutionContext;
import application.rest.v1.actions.ResolutionContext.ResolvedValue;
import application.rest.v1.actions.ValidationException;
import application.rest.v1.actions.PatternException;
import application.rest.v1.configmaps.ConfigMapProcessor;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.BatchV1Api;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1JobSpec;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PodSecurityContext;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1PodTemplateSpec;

import com.ibm.kappnav.logging.Logger;

@Path("/resource")
@Tag(name = "actions", description="kAppNav Actions API")
public class ActionsEndpoint extends KAppNavEndpoint {
    
    private static final String className = ActionsEndpoint.class.getName();
    
    private static final String CMD_NOT_FOUND = "Command Not Found";
    
    private static final String APPLICATION_KIND = "Application";
    private static final String JOB_KIND = "Job";
    private static final String KAPPNAV_PREVIX = "kappnav";
    private static final String GLOBAL_NAMESPACE = KAppNavConfig.getkAppNavNamespace();
    
    private static final String ACTION_PROPERTY_NAME = "action";
    private static final String KIND_PROPERTY_NAME = "kind";
    
    private static final String COMMANDS_PROPERTY_NAME = "commands";
    private static final String ACTION_MAP_PROPERTY_NAME = "action-map";

    private static final String TIME_PROPERTY_NAME = "time";
    
    private static final String IMAGE_PROPERTY_NAME = "image";
    private static final String CMD_PATTERN_PROPERTY_NAME = "cmd-pattern";
    private static final String TEXT_PROPERTY_NAME = "text";
    private static final String REQUIRES_INPUT_PROPERTY_NAME = "requires-input";
    
    // App nav job annotations
    private static final String KAPPNAV_JOB_ACTION_TEXT = "kappnav-job-action-text";
    
    // App nav job labels
    private static final String KAPPNAV_JOB_TYPE = "kappnav-job-type";
    private static final String KAPPNAV_JOB_ACTION_NAME = "kappnav-job-action-name";
    
    private static final String KAPPNAV_JOB_COMPONENT_KIND = "kappnav-job-component-kind";
    private static final String KAPPNAV_JOB_COMPONENT_SUB_KIND = "kappnav-job-component-sub-kind";
    private static final String KAPPNAV_JOB_COMPONENT_NAME = "kappnav-job-component-name";
    private static final String KAPPNAV_JOB_COMPONENT_NAMESPACE = "kappnav-job-component-namespace";
    
    private static final String KAPPNAV_JOB_APPLICATION_NAME = "kappnav-job-application-name";
    private static final String KAPPNAV_JOB_APPLICATION_NAMESPACE = "kappnav-job-application-namespace";
    
    // App nav job label values
    private static final String KAPPNAV_JOB_COMMAND_TYPE = "command";
    private static final String KAPPNAV_JOB_RESOURCE_KIND = "Job";

    private static final String STATUS_PROPERTY_NAME = "status";
    private static final String COMPLETION_TIME_PROPERTY_NAME = "completionTime";

    // Annotation properties.
    private static final String METADATA_PROPERTY_NAME = "metadata";
    private static final String ANNOTATIONS_PROPERTY_NAME = "annotations";
    private static final String KAPPNAV_JOB_USER_ID = "kappnav-job-user-id";

    @Inject
    private ComponentInfoRegistry registry;
    
    @Inject
    private KAppNavConfig config;
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{resource-name}/{resource-kind}")
    @Operation(
            summary = "Resolves an action config map action pattern.",
            description = "Returns the resolved action pattern string for a kAppNav action config map action pattern."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "422", description = "Unprocessable Entity (User input is invalid)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response resolve(@Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("resource-name") @Parameter(description = "The name of the resource") String name,
            @PathParam("resource-kind") @Parameter(description = "The Kubernetes resource kind for the resource") String kind,
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the resource") String namespace,
            @DefaultValue("") @QueryParam("action-pattern") @Parameter(description = "The action pattern to resolve") String pattern) {
        try {
            final ApiClient client = getApiClient();
            ResponseBuilder builder = Response.ok(new ActionSubstitutionResolverResponse(resolve(client, name, kind, "", namespace, pattern)).getJSON());
            return builder.build();
        }
        catch (IOException | ApiException | PatternException e) {
            String msg = null;
            if (e instanceof PatternException) {
                msg = "pattern-error: " + e.getMessage();
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, "resolve", Logger.LogType.ERROR, "Caught PatternException returning status: " + getResponseCode(e) + " " + msg);
                }
            } else if (e instanceof ApiException) {
                msg = "input-error: " + e.getMessage(); 
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, "resolve", Logger.LogType.ERROR, "Caught ApiException returning status: " + getResponseCode(e) + " " + msg);
                }
            } else {
                msg = "internal-error: An internal error occurred in resolving an action config map pattern. error: " + e.getMessage();
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, "resolve", Logger.LogType.ERROR, "Caught IOException returning status: " + getResponseCode(e) + " " + msg);
                }
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(msg)).build();
        } 
    }
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{application-name}/execute/command/{command-action-name}")
    @Operation(
            summary = "Resolves a command action pattern from the action config map and creates a job in Kubernetes from the resolved action.",
            description = "Returns the Kubernetes job created from the resolved command action pattern."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "404", description = "Not Found (Command not found in Config Map)"),
        @APIResponse(responseCode = "422", description = "Unprocessable Entity (User input is invalid)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    @RequestBody(description = "User Input Map")
    public Response executeApplicationCommand(String jsonstr, @Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("application-name") @Parameter(description = "The name of the application") String name,
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the application") String namespace,
            @PathParam("command-action-name") @Parameter(description = "The name of the command action") String commandName,
            @CookieParam("kappnav-user") @DefaultValue("") @Parameter(description = "The user that submitted the command action") String user) {
        return executeCommand(jsonstr, name, APPLICATION_KIND, "", namespace, commandName, null, null, user);
    }
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{application-name}/{component-name}/{component-kind}/execute/command/{command-action-name}")
    @Operation(
            summary = "Resolves a command action pattern from the action config map and creates a job in Kubernetes from the resolved action.",
            description = "Returns the Kubernetes job created from the resolved command action pattern."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "404", description = "Not Found (Command not found in Config Map)"),
        @APIResponse(responseCode = "422", description = "Unprocessable Entity (User input is invalid)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    @RequestBody(description = "User Input Map")
    public Response executeComponentCommand(String jsonstr, @Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("application-name") @Parameter(description = "The name of the application") String appName,
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("application-namespace") @Parameter(description = "The namespace of the application") String appNamespace,
            @Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("component-name") @Parameter(description = "The name of the component") String name,
            @PathParam("component-kind") @Parameter(description = "The Kubernetes resource kind for the component") String kind,
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the component") String namespace,
            @PathParam("command-action-name") @Parameter(description = "The name of the command action") String commandName,
            @CookieParam("kappnav-user") @DefaultValue("") @Parameter(description = "The user that submitted the command action") String user) {
        return executeCommand(jsonstr, name, kind, "", namespace, commandName, appName, appNamespace, user);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/command-time")
    @Operation(
            summary = "Retrieve current time to specify as query value for retrieving Kubernetes jobs using /commands api. Time returned in yyyy-MM-dd'T'HH:mm:ss.000Z format",
            description = "Retrieve current time for retrieving jobs."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response getCommandTime() { 

        final String methodName = "getCommandTime";
        if (Logger.isEntryEnabled()) {
            Logger.log(className, methodName, Logger.LogType.ENTRY, "");
        } 

        // convert current time to {time: value} JSON where value is yyyy-MM-dd'T'HH:mm:ss.000Z format
        // This format matches the format returned in the completionTime field for jobs.  Kubernetes
        // stores timestamps only to the second, the 000 (millisecond) is just to unify timestamp format
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.'000Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String formattedTimestamp = null;
        final Date date = new Date();
        try { 
            formattedTimestamp = dateFormat.format( date );
        }
        catch(Exception e) { 
            String msg = "Internal error parsing date: " + date;
            if (Logger.isErrorEnabled()) {
                Logger.log(className, methodName, Logger.LogType.ERROR, msg); 
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(msg)).build();
        }
        JsonObject timeJSON = new JsonObject();
        JsonElement timeElement = new JsonPrimitive(formattedTimestamp); 
        timeJSON.add(TIME_PROPERTY_NAME, timeElement);

        if (Logger.isExitEnabled()) {
            Logger.log(className, methodName, Logger.LogType.EXIT, "timeJSON="+timeJSON);
        }
        return Response.ok(timeJSON.toString()).build(); 
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/commands")
    @Operation(
            summary = "Retrieve the list of Kubernetes jobs for command actions, optionally filtered by user and time and only jobs with completion timestamp newer than specified timestamp are returned",
            description = "Returns two lists: the list of Kubernetes jobs for command actions; the list of job actions for the jobs."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response getCommands(@CookieParam("kappnav-user") @DefaultValue("") @Parameter(description = "The user that submitted the command action") String user,
                                @QueryParam("time") @DefaultValue("") @Parameter(description = "The job completion time stamp in yyyy-MM-dd'T'HH:mm:sss format") String time) {

        final String methodName = "getCommands";
        if (Logger.isEntryEnabled()) {
            Logger.log(className, methodName, Logger.LogType.ENTRY, "user=" + user + ", time=" + time);
        }
        try {
            final ApiClient client = getApiClient();
            
            // Build the selector for the query.
            final Selector s = new Selector().addMatchLabel(KAPPNAV_JOB_TYPE, KAPPNAV_JOB_COMMAND_TYPE);
            
            // convert time to timestamp in yyyy-MM-dd'T'HH:mm:sss format
            Timestamp timelaterTimestamp = convertTimeStringToTimestamp(time);
            
            final String labelSelector = s.toString();           
            // Query the list of jobs from Kubernetes and return the list to the caller.
            final BatchV1Api batch = new BatchV1Api();
            batch.setApiClient(client);

            if (Logger.isDebugEnabled()) {
                Logger.log(className, methodName, Logger.LogType.DEBUG, 
                    "GLOBAL_NAMESPACE=" + GLOBAL_NAMESPACE + " ,labelSelector=" + labelSelector);
            }

            List<JsonObject> commands = getItemsAsList(client, batch.listNamespacedJob(GLOBAL_NAMESPACE, null, null, null, null, labelSelector, null, null, null, null));           
            final CommandsResponse response = new CommandsResponse();           
            
            commands.forEach(v -> {
                if (v.get(KIND_PROPERTY_NAME) == null) {
                    v.addProperty(KIND_PROPERTY_NAME, JOB_KIND);
                }

                if(user != null && !user.isEmpty()) {
                    // Only return jobs belonging to the user
                    final String job_username = getCommandUserName(v);
                    if(! user.equals(job_username)) {
                        // Completely skip this command because it does
                        // not belong to the user requested by the API caller
                        return;
                    }
                }

                if (time == null || time.isEmpty()) {   
                    //no time specified, return all jobs
                    response.add(v);
                }  else {  
                    JsonObject status = v.getAsJsonObject(STATUS_PROPERTY_NAME);
                    if (status != null) {
                        JsonElement e = status.get(COMPLETION_TIME_PROPERTY_NAME);
                        if (e != null && e.isJsonPrimitive()) {
                            String completionTime = e.getAsString();
                            if (completionTime != null) {
                                // convert job completion time to timestamp in yyyy-MM-dd'T'HH:mm:sss format
                                try {
                                    Timestamp completionTimestamp = convertTimeStringToTimestamp(completionTime);
                                    // Only jobs with completion time stamp newer than specified time stamp are returned or no timestamp specified. 
                                    if (completionTimestamp.after(timelaterTimestamp)) {                                    
                                        response.add(v);
                                    }       
                                } catch (Exception ex)  {
                                    if (Logger.isDebugEnabled()) {
                                        Logger.log(className, methodName, Logger.LogType.DEBUG, "Caught an exception " + ex.toString());
                                    }
                                }                                        
                            }
                        }
                    }
                }
            });

            // If there are jobs, get actions available for jobs and add to response 
            if ( ! commands.isEmpty() && response.size() > 0) { 
                JsonObject job= commands.get(0); // get first job, any job, so we can retrieve actions 
                final ConfigMapProcessor processor = new ConfigMapProcessor(KAPPNAV_JOB_RESOURCE_KIND);
                JsonObject actionsMap = processor.getConfigMap(client, job, ConfigMapProcessor.ConfigMapType.ACTION);
                response.addActions(actionsMap);
            }

            final String responseJSON = response.getJSON();
            if (Logger.isExitEnabled()) {
                Logger.log(className, methodName, Logger.LogType.EXIT, "responseJSON=" + responseJSON);
            }
            return Response.ok(responseJSON).build(); 
        }
        catch (IOException | ApiException e ) {
            String msg = null;          
            if (e instanceof ApiException)  {              
                msg = "input-error: " + e.getMessage(); 
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, methodName, Logger.LogType.ERROR, "Caught ApiException returning status: " + getResponseCode(e) + " " + msg); 
                }
            } else {
                msg = "internal-error: An internal error occurred in retrieving list of kubernetes jobs. error: " + e.getMessage();  
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, methodName, Logger.LogType.ERROR, "Caught IOException returning status: " + getResponseCode(e) + " " + msg);   
                }          
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(msg)).build();
        } 
    }
    
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/command/{job-name}")
    @Operation(
            summary = "Deletes a command action job.",
            description = "Delete the specified command action job. The namespace of the job is assumed to be 'kappnav'."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response deleteCommand(@Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("job-name") @Parameter(description = "The name of the command action job") String jobName) {
        try {
            final ApiClient client = getApiClient();            
            final BatchV1Api batch = new BatchV1Api();
            batch.setApiClient(client);
            
            // Check that the specified job exists and is a command action.
            final Selector s = new Selector().addMatchLabel(KAPPNAV_JOB_TYPE, KAPPNAV_JOB_COMMAND_TYPE);
            JsonObject job = getItemAsObject(client, batch.readNamespacedJob(jobName, GLOBAL_NAMESPACE, null, null, null));
            if (!s.matches(job)) {
                String msg = "input-error: Job " + jobName + " is not found in command action.";
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, "deleteCommand", Logger.LogType.ERROR, msg); 
                }
                throw new ApiException(404, msg);
            }
            
            // Delete the specified job.
            final V1DeleteOptions options = new V1DeleteOptions();
            batch.deleteNamespacedJob(jobName, GLOBAL_NAMESPACE, options, null, 0, true, "");
            return Response.ok(getStatusMessageAsJSON("OK")).build();
        }
        catch (JsonSyntaxException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof IllegalStateException) {
                final IllegalStateException _cause = (IllegalStateException) e.getCause();
                final String message = _cause.getMessage();
                if (message != null && message.contains("Expected a string but was BEGIN_OBJECT")) {
                    // Workaround for an issue in the Kubernetes API client. The job was deleted,
                    // but due to a defect in the client an error occurs in constructing the V1Status
                    // object return value. See "https://github.com/kubernetes-client/java/issues/86"
                    // for more details.
                    return Response.ok(getStatusMessageAsJSON("OK")).build();
                }
            }
            String msg = "syntax-error: " + e.getMessage();
            if (Logger.isErrorEnabled()) {
                Logger.log(className, "deleteCommand", Logger.LogType.ERROR, "Caught JsonSyntaxException returning status: " + getResponseCode(e) + " " + msg);
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(msg)).build();
        }
        catch (IOException | ApiException e) {
            String msg = null;           
            if (e instanceof ApiException) {
                msg = "input-error: " + e.getMessage(); 
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, "deleteCommand", Logger.LogType.ERROR, "Caught ApiException returning status: " + getResponseCode(e) + " " + msg);
                }
            }
            else {
                msg = "internal-error: " + "An internal error occurred in deleting a command action job. error:" + e.getMessage(); 
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, "deleteCommand", Logger.LogType.ERROR, "Caught IOException returning status: " + getResponseCode(e) + " " + msg);
                }
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(msg)).build();
        } 
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{resource-name}/{resource-kind}/actions")
    @Operation(
            summary = "Retrieves an action config map for the specified Kubernetes resource.",
            description = "Returns the action config map for the specified Kubernetes resource."
            )
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"),
        @APIResponse(responseCode = "207", description = "Multi-Status (Error from Kubernetes API)"),
        @APIResponse(responseCode = "400", description = "Bad Request (Malformed input)"),
        @APIResponse(responseCode = "500", description = "Internal Server Error")})
    public Response getActionMap(@Pattern(regexp = NAME_PATTERN_ONE_OR_MORE) @PathParam("resource-name") @Parameter(description = "The name of the resource") String name,
            @PathParam("resource-kind") @Parameter(description = "The Kubernetes resource kind for the resource") String kind,
            @Pattern(regexp = NAME_PATTERN_ZERO_OR_MORE) @DefaultValue("default") @QueryParam("namespace") @Parameter(description = "The namespace of the resource") String namespace) {
        try {
            final ApiClient client = getApiClient();
            final JsonObject resource = getResource(client, name, kind, "", namespace);
            final JsonObject map;
                
            if (resource != null) {
                ConfigMapProcessor processor = new ConfigMapProcessor(kind);
                map = processor.getConfigMap(client, resource, ConfigMapProcessor.ConfigMapType.ACTION);
            }
            else {
                map = new JsonObject();
            }
            return Response.ok(map.toString()).build();
        }
        catch (IOException | ApiException e) {
            String msg = null;           
            if (e instanceof ApiException) {
                msg = "input-error: " + e.getMessage(); 
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, "getActionMap", Logger.LogType.ERROR, "Caught ApiException returning status: " + getResponseCode(e) + " " + msg);
                }
            } else {   
                msg = "internal-error: An internal error occurred in retrieving an action config map. error: " + e.getMessage();
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, "getActionMap", Logger.LogType.ERROR, "Caught IOException returning status: " + getResponseCode(e) + " " + msg);
                }
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(msg)).build();
        }                  
    }
    
    private String resolve(ApiClient client, String name, String kind, String apiVersion, String namespace, String pattern) throws ApiException {
        String methodName = "resolve";
        if (Logger.isEntryEnabled()) {
            Logger.log(className, methodName, Logger.LogType.ENTRY, "Name=" + name + ", kind="+ kind + ", apiVersion="+apiVersion + ", namespace="+namespace + ", pattern="+pattern);
        }

        final JsonObject resource; 
        final ResolvedValue resolvedValue;
        try { 
            resource = getResource(client, name, kind, apiVersion, namespace);      
            // Add a 'kind' property to the resource if it is missing.
            if (resource.get(KIND_PROPERTY_NAME) == null) {
                resource.addProperty(KIND_PROPERTY_NAME, kind);
            }           
            final ResolutionContext context = new ResolutionContext(client, registry, resource, kind);
            resolvedValue = context.resolve(pattern);
        } catch (ApiException e) {
            String msg = e.getMessage();
            if (Logger.isErrorEnabled()) {
                Logger.log(className, methodName, Logger.LogType.ERROR, "Caught ApiException " + msg);
            }
            throw new ApiException(404, msg);           
        } catch (PatternException e) {
            String msg = e.getMessage();
            if (Logger.isErrorEnabled()) {
                Logger.log(className, methodName, Logger.LogType.ERROR, "Caught PatternException " + msg);
            }
            throw new ApiException(207, msg);
        } 
        if (Logger.isExitEnabled()) {
            Logger.log(className, methodName, Logger.LogType.EXIT, "ResolvedValue="+resolvedValue.getValue());
        }
        return resolvedValue.getValue();     
    }
    
    private Response executeCommand(String jsonstr, String name, String kind, String apiVersion, String namespace,
            String commandName, String appName, String appNamespace, String user) {

        final String methodName = "executeCommand";
        if (Logger.isErrorEnabled()) {
            Logger.log(className, methodName, Logger.LogType.ENTRY,
                    "name=" + name + ", kind=" + kind + ", apiVersion=" + apiVersion + ", namespace=" + namespace
                            + ", commandName=" + commandName + ", appName=" + appName + ", appNamespace=" + appNamespace
                            + ", user=" + user);
        }

        try {
            final ApiClient client = getApiClient();
            final JsonObject resource;
            try { 
                resource = getResource(client, name, kind, apiVersion, namespace);
            } catch (ApiException e) {
                String msg = e.getMessage();
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, methodName, Logger.LogType.ERROR, "Caught ApiException " + msg);
                }
                throw new ApiException (404, msg);
            }
            
            // Add a 'kind' property to the resource if it is missing.
            if (resource.get(KIND_PROPERTY_NAME) == null) {
                resource.addProperty(KIND_PROPERTY_NAME, kind);
            }
            final ResolutionContext context = new ResolutionContext(client, registry, resource, kind);
            
            // Retrieve the command action from the config map.   
            final JsonObject cmdAction = context.getCommandAction(commandName);  
            if (cmdAction == null) {
                String msg = "Command action " + commandName + " is not found in config map.";
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, methodName, Logger.LogType.ERROR, msg);
                }
                throw new ApiException(404, msg);
            }
                        
            // Process user input if required.
            processUserInput(jsonstr, cmdAction, context);
            
            // Retrieve the image name.
            final JsonElement imageProp = cmdAction.get(IMAGE_PROPERTY_NAME);
            if (imageProp == null || !imageProp.isJsonPrimitive()) {
                String msg = "Image was not specified in the command action.";
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, methodName, Logger.LogType.ERROR, msg);
                }
                throw new ApiException(404, msg);
            }
            final String imageName = imageProp.getAsString();
            
            // Retrieve the command pattern.
            final JsonElement cmdPatternProp = cmdAction.get(CMD_PATTERN_PROPERTY_NAME);
            if (cmdPatternProp == null || !cmdPatternProp.isJsonPrimitive()) {
                String msg = "cmd-pattern was not specified in the command action.";
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, methodName, Logger.LogType.ERROR, msg);
                }
                throw new ApiException(404, msg);
            }
            final String cmdPattern = cmdPatternProp.getAsString();
            
            // Retrieve the display text.
            final JsonElement textProp = cmdAction.get(TEXT_PROPERTY_NAME);
            final String text = (textProp != null && textProp.isJsonPrimitive()) ? textProp.getAsString() : null;
            
            // Resolve the command pattern.
            final ResolvedValue resolvedValue = context.resolve(cmdPattern);
            if (!resolvedValue.isFullyResolved()) {
                // If the resolution failed we should stop here instead of generating a bad job.
                // REVISIT: Message translation required.
                String msg = "An internal error occurred in the resolution of the command action pattern" + cmdPattern;
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, methodName, Logger.LogType.ERROR, msg);
                }
                throw new KAppNavException(msg);
            }
            final String resolvedPattern = resolvedValue.getValue();
            final CommandLineTokenizer tokenizer = new CommandLineTokenizer(resolvedPattern);
            
            // Construct the pod template / container from the request.
            final V1Container container = new V1Container();
            container.setName(KAPPNAV_PREVIX + "-" + UUID.randomUUID().toString());
            container.setImage(imageName);
            final List<String> command = new ArrayList<>();
            tokenizer.forEach(parameter -> {
                command.add(parameter);
            });
            container.setCommand(command);
            
            final V1PodSpec spec = new V1PodSpec();
            spec.setContainers(Collections.singletonList(container));
            spec.setRestartPolicy("Never");
            setSecurityContextAndServiceAccountName(client, spec);
            
            final V1PodTemplateSpec podTemplate = new V1PodTemplateSpec();
            podTemplate.setSpec(spec);
            
            // Create and populate the job object.
            final V1Job job = new V1Job();
            job.setApiVersion("batch/v1");
            job.setKind("Job");
            final V1ObjectMeta meta = new V1ObjectMeta();
            meta.setName(KAPPNAV_PREVIX + "-" + UUID.randomUUID().toString());
            
            // Add context labels to the job, allowing for queries using selectors.
            final Map<String,String> labels = createJobLabels(client, resource, name, kind, 
                    namespace, appName, appNamespace, commandName);
            meta.setLabels(labels);
            
            final Map<String,String> annotations = createJobAnnotations(text, user);
            if (annotations != null) {
                meta.setAnnotations(annotations);
            } 
            
            job.setMetadata(meta);
            final V1JobSpec jobSpec = new V1JobSpec();
            job.setSpec(jobSpec);
            jobSpec.setBackoffLimit(4);
            jobSpec.setTemplate(podTemplate);
            
            // Submit the job to Kubernetes and return the job object to the caller.
            final BatchV1Api batch = new BatchV1Api();
            batch.setApiClient(client);
            final JsonObject response = getItemAsObject(client, batch.createNamespacedJob(GLOBAL_NAMESPACE, job, null));
            if (Logger.isExitEnabled()) {
                Logger.log(className, methodName, Logger.LogType.EXIT, response.toString());
            }
            return Response.ok(response.toString()).build();
        }
        catch (IOException | JsonSyntaxException | ApiException | KAppNavException | ValidationException | PatternException e) {
            String msg = null;
            if (e instanceof JsonSyntaxException)
                msg = "syntax-error: ";
            if (e instanceof ApiException)               
                msg = "input-error: ";
            if (e instanceof IOException || e instanceof KAppNavException)
                msg = "internal-error: ";
            if (e instanceof ValidationException) 
                msg = "validation-error: ";
            if (e instanceof PatternException) 
                msg = "pattern-error: ";       
            msg = msg + e.getMessage();     
            if (Logger.isErrorEnabled()) {
                Logger.log(className, methodName, Logger.LogType.ERROR, "Caught Exception " + msg);
            }
            return Response.status(getResponseCode(e)).entity(getStatusMessageAsJSON(msg)).build();
        } 
    }
    
    private void processUserInput(String jsonstr, JsonObject action, ResolutionContext context) throws JsonSyntaxException, 
        ValidationException, KAppNavException {
        String methodName = "processUserInput";
        if (Logger.isEntryEnabled()) {
            Logger.log(className, methodName, Logger.LogType.ENTRY, "JsonStr=" + jsonstr + ", action= " + action.toString() + ", context=" + context.toString());
        }

        final JsonElement requiresInputProp = action.get(REQUIRES_INPUT_PROPERTY_NAME);
        if (requiresInputProp != null && requiresInputProp.isJsonPrimitive()) {
            final String requiresInput = requiresInputProp.getAsString();
            final JsonObject fields = context.getInputFields(requiresInput);
            if (fields != null) {
                final JsonObject userInput;
                if (jsonstr != null && !jsonstr.trim().isEmpty()) {
                    JsonParser parser = new JsonParser(); 
                    JsonElement element = parser.parse(jsonstr);
                    if (element.isJsonObject()) {
                        userInput = element.getAsJsonObject();
                    }
                    else {
                        // REVISIT: Message translation required.
                        String msg = "The inputs " + jsonstr + " specified was in the wrong format. It is expected to be a map.";
                        if (Logger.isErrorEnabled()) {
                            Logger.log(className, methodName, Logger.LogType.ERROR, msg);
                        }
                        throw new ValidationException(msg);
                    }
                }
                else {
                    userInput = null;
                }
                if (Logger.isExitEnabled()) {
                    Logger.log(className, methodName, Logger.LogType.EXIT, "userInput="+userInput);
                }
                context.setUserInput(userInput, fields);
            }
            else {
                // REVISIT: Message translation required.
                String msg = "An error occurred in the resolution of the field definitions for input \"" + requiresInput + "\"." ;
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, methodName, Logger.LogType.ERROR, msg);
                }
                throw new KAppNavException(msg);
            }
        }
    }
      
    // REVISIT: Should re-think how we set this for user provided command actions.
    private void setSecurityContextAndServiceAccountName(ApiClient client, V1PodSpec spec) {
        final V1PodSecurityContext podSecurityContext = new V1PodSecurityContext();
        podSecurityContext.setRunAsNonRoot(true);
        podSecurityContext.setRunAsUser(1001L);
        spec.setSecurityContext(podSecurityContext);
        if (config == null) {
            // Initialize the config here if CDI failed to do it.
            config = new KAppNavConfig(client);
        }
        spec.setServiceAccountName(config.getkAppNavServiceAccountName()); 
    }
    
    private Map<String,String> createJobAnnotations(String text, String user) {
        if (text != null && !text.isEmpty()) {
            Map<String, String> annotations = new HashMap<String, String>();
            annotations.put(KAPPNAV_JOB_ACTION_TEXT, text);
            annotations.put(KAPPNAV_JOB_USER_ID, user);
            return Collections.unmodifiableMap(annotations);
        }
        return null;
    }
    
    private Map<String,String> createJobLabels(ApiClient client, JsonObject resource, String name, String kind, 
            String namespace, String appName, String appNamespace, String actionName) {
        String methodName = "createJobLabels";
        if (Logger.isEntryEnabled()) {
            Logger.log(className, methodName, Logger.LogType.ENTRY, "Resource=" + resource.toString() + ", name="+ name + ", kind=" + kind + ", namespace="+namespace + ", appName=" + appName + ", appNamespace=" + appNamespace + ", actionName=" + actionName);
        }

        final Map<String,String> labels = new HashMap<>();
        labels.put(KAPPNAV_JOB_TYPE, KAPPNAV_JOB_COMMAND_TYPE);
        labels.put(KAPPNAV_JOB_ACTION_NAME, actionName);

        // If appName is not set this resource is an application. Only set the application labels.
        if (appName == null || appName.isEmpty()) {
            labels.put(KAPPNAV_JOB_APPLICATION_NAME, name);
            labels.put(KAPPNAV_JOB_APPLICATION_NAMESPACE, namespace);
        }
        else {
            // Set application labels.
            labels.put(KAPPNAV_JOB_APPLICATION_NAME, appName);
            labels.put(KAPPNAV_JOB_APPLICATION_NAMESPACE, appNamespace);
            // Set component labels.
            labels.put(KAPPNAV_JOB_COMPONENT_KIND, kind);
            // Set the sub-kind if it's available.
            final String subKind = KAppNavEndpoint.getComponentSubKind(resource);
            if (subKind != null && !subKind.isEmpty()) {
                labels.put(KAPPNAV_JOB_COMPONENT_SUB_KIND, subKind);
            }
            labels.put(KAPPNAV_JOB_COMPONENT_NAME, name);
            labels.put(KAPPNAV_JOB_COMPONENT_NAMESPACE, namespace);
        }
        if (Logger.isExitEnabled()) {
            Logger.log(className, methodName, Logger.LogType.EXIT, labels.toString());
        }
        return labels;
    }
    
    private JsonObject getResource(ApiClient client, String name, String kind, String apiVersion, String namespace) throws ApiException {
        String methodName = "resolve";
        if (Logger.isEntryEnabled()) {
            Logger.log(className, methodName, Logger.LogType.ENTRY, "Name=" + name + ", kind=" + kind + ", apiVersion=" + apiVersion + ", namespace=" + namespace);
        }

        if (registry == null) {
            // Initialize the registry here if CDI failed to do it.
            registry = new ComponentInfoRegistry(client);
        }
    
        if (apiVersion == null || apiVersion.trim().length() == 0) {
            apiVersion = ComponentInfoRegistry.CORE_KIND_TO_API_VERSION_MAP.get(kind);
            if (apiVersion == null) {
                if (Logger.isErrorEnabled()) {
                    Logger.log(className, "getResource", Logger.LogType.ERROR, "Api version is null.");
                }
                throw new ApiException(400, "getResource Unknown kind: " + kind);
            }
        }
        final Object o = registry.getNamespacedObject(client, kind, apiVersion, namespace, name);
        if (Logger.isExitEnabled()) {
            Logger.log(className, methodName, Logger.LogType.EXIT, "");
        }
        return getItemAsObject(client, o);
    }
    
    static final class ActionSubstitutionResolverResponse {
        private final JsonObject o;
        public ActionSubstitutionResolverResponse(String resolvedAction) {
            o = new JsonObject();
            o.addProperty(ACTION_PROPERTY_NAME, resolvedAction);
        }
        public String getJSON() {
            return o.toString();
        }
    }
    
    static final class CommandsResponse {
        private final JsonObject o;
        private final JsonArray commands;
        // Constructs:
        // {
        //   commands: [ {...}, {...}, ... ]
        // }
        public CommandsResponse() {
            o = new JsonObject();
            o.add(COMMANDS_PROPERTY_NAME, commands = new JsonArray());
        }
        public void add(final JsonObject command) {
            commands.add(command);
        }
        public void addActions(final JsonObject actions) { 
            o.add(ACTION_MAP_PROPERTY_NAME, actions); 
        }  
        public int size() {
            return commands.size();
        }
        public String getJSON() {
            return o.toString();
        }
    }

    private Timestamp convertTimeStringToTimestamp(String time) throws ApiException {
        Timestamp timestamp = null;
        try {
            if (time != null && !time.isEmpty()) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:sss");
                Date parsedDate = dateFormat.parse(time);
                timestamp = new java.sql.Timestamp(parsedDate.getTime());
            }
        } catch (Exception e) { 
            String msg = e.getMessage() + ". The correct format is yyyy-MM-dd'T'HH:mm:sss";
            if (Logger.isErrorEnabled()) {
                Logger.log(className, "convertTimeStringToTimestamp", Logger.LogType.ERROR, "Caught exception " + msg);
            }
            throw new ApiException(msg);
        } 
        return timestamp;
    }
   
    // Decodes a URL encoded string using `UTF-8`
    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ex) {
            if (Logger.isErrorEnabled()) {
                Logger.log(className, "urlDecode", Logger.LogType.ERROR, "Caught UnsupportedEncodingException " + ex.toString());
            }
            throw new RuntimeException(ex.getCause());
        }
    }

    /**
     *  Return the username associated with the command action
     * @param job - JsonObject of the job details
     * @return String - username who created the job, else empty String
     */
    private String getCommandUserName(JsonObject job) {
        final String methodName = "getCommandUserName";
        if (Logger.isEntryEnabled()) {
            Logger.log(className, methodName, Logger.LogType.ENTRY, "job=" + job);
        }
        String result = "";

        final JsonObject metadata = job.getAsJsonObject(METADATA_PROPERTY_NAME);
        if (metadata != null) {
            final JsonObject annotations = metadata.getAsJsonObject(ANNOTATIONS_PROPERTY_NAME);
            if (annotations != null) {
                JsonElement userId = annotations.get(KAPPNAV_JOB_USER_ID);
                if (userId != null) {
                    result = userId.getAsString();
                }
            }
        }

        if (Logger.isExitEnabled()) {
            Logger.log(className, methodName, Logger.LogType.EXIT, "result=" + result);
        }
        return result;
    }
}
