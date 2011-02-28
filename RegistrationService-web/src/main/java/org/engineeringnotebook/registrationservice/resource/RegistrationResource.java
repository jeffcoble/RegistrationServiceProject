/*
 * Copyright 2011 Jeff Coble <jeffrey.a.coble@gmail.com> http://engineeringnotebook.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.engineeringnotebook.registrationservice.resource;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.ws.rs.QueryParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.engineeringnotebook.registrationservice.core.RegistrationServiceRemoteInterface;

/**
 * The resource backing restful web service calls to register a user
 * 
 * @author Jeff Coble <jeffrey.a.coble@gmail.com> http://engineeringnotebook.org
 */

@Produces("text/plain")
@Path("/register")
public class RegistrationResource {
    @javax.ws.rs.core.Context
    UriInfo uriInfo;
    private RegistrationServiceRemoteInterface registrationServiceEJB; 
    private static final Logger logger = Logger.getLogger(RegistrationResource.class.getName());
   
    /**
     * Called to get the reference to the EJB
     */
    private void connectEJB() {

        Context context;
        try
        {
            logger.log(Level.FINE, "Trying to get the EJB reference");
            context = new InitialContext();
            registrationServiceEJB = (RegistrationServiceRemoteInterface)context.lookup("org.engineeringnotebook.registrationservice.core.RegistrationServiceRemoteInterface");
        } catch (NamingException e)
        {
            logger.log(Level.FINE, "Failed to get the EJB reference");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
   
    
    /**
     * Fulfills a restful web service call to get the authorization link for
     * Twitter authorization as part of the OAuth authorization workflow
     * 
     * The method will process HTTP GET requests and produces content 
     * identified by the MIME Media type "text/plain"
     * 
     * @param twitterScreenName
     * @return The Twitter authorization URL
     */
    @GET
    @Produces("text/plain")
    public String getAuthorizationURL(@QueryParam("screenname") String twitterScreenName) {
        logger.log(Level.FINE, "Registration resource: getAuthorizationURL");
        
        String result = null;
        if(twitterScreenName != null){
            connectEJB();
            result = registrationServiceEJB.requestRegistration(twitterScreenName);
        }
        return result;    
    }
   
    /**
     * Once the user has retrieved the authorization URL and visited that URL
     * to retrieve the verification token, that token is provided to the service
     * via this restful web service call
     * 
     * The method will process HTTP PUT requests and will accept content 
     * identified by the MIME Media type "text/plain"
     * 
     * @param twitterScreenName
     * @param verificationToken
     * @return
     */
    @POST
    @Consumes("text/plain")   
    @Path("/settoken")
    public Response setVerificationToken(@QueryParam("screenname") String twitterScreenName, @QueryParam("token") String verificationToken) {
        logger.log(Level.FINE, "Registration resource: setVerificationToken");
        
        URI uri =  uriInfo.getAbsolutePath();
         
        if(twitterScreenName != null){
            connectEJB();
            registrationServiceEJB.setVerificationToken(twitterScreenName, verificationToken);
        }  
        //need to figure out how to set the response if input parms are invalid
        return Response.created(uri).build();
    } 

}
