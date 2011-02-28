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

package org.engineeringnotebook.registrationservice.ejb;

import javax.ejb.Stateless;
import javax.ejb.SessionContext;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import javax.annotation.Resource;
import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.ArrayList;
import com.spaceprogram.simplejpa.EntityManagerFactoryImpl;
import org.scribe.model.Token;
import org.engineeringnotebook.snrdm.core.oauth.OAuthHandler;
import org.engineeringnotebook.snrdm.core.oauth.OAuthUtilities;
import org.engineeringnotebook.snrdm.entity.UserCredential;
import org.engineeringnotebook.registrationservice.core.RegistrationServiceLocalInterface;
import org.engineeringnotebook.registrationservice.core.RegistrationServiceRemoteInterface;
import org.engineeringnotebook.snrdm.core.utilities.ClassPathBuilder;


/**
 * Stateless EJB that interacts with the Twitter API to support OAuth authorization.
 * Stores the user credentials in an Amazon SimpleDB database
 * 
 * @author Jeff Coble <jeffrey.a.coble@gmail.com> http://engineeringnotebook.org
 */
@Stateless
public class RegistrationServiceBeanImpl implements RegistrationServiceRemoteInterface, RegistrationServiceLocalInterface {

    private static final Logger logger = Logger.getLogger(RegistrationServiceBeanImpl.class.getName());
    private static EntityManagerFactory factory; 
    private OAuthHandler oauthHandler;
    
    //Amazon API keys
    @Resource(name="org/engineeringnotebook/amazonaccesskey") 
    private String amazonAccessKeyValue;
    @Resource(name="org/engineeringnotebook/amazonsecretkey")    
    private String amazonSecretKeyValue;    
    
    //Twitter OAuth Keys
    @Resource(name="org/engineeringnotebook/twitterconsumerkey") 
    private String twitterConsumerKey;
    @Resource(name="org/engineeringnotebook/twitterconsumersecretkey") 
    private String twitterConsumerSecretKey;    
        
    public RegistrationServiceBeanImpl() {
        logger.log(Level.INFO, "Creating the RegistrationServiceBean");  
    }
    
    /**
     * We need this because dependency injection of the member variables seems 
     * to happen after the constructor is called.
     */
    @PostConstruct
    private void initialize() {
        initializeSimpleJPA();  
        oauthHandler = new OAuthHandler(twitterConsumerKey, twitterConsumerSecretKey);
        
    }
    
    /**
     * Sets up the simplejpa entity manager
     */
    private void initializeSimpleJPA() {
        
        List<Class> classList = new ArrayList();
        ClassPathBuilder cpBuilder = new ClassPathBuilder();
        
        classList.add(UserCredential.class);
        
        Map<String,String> props = new HashMap<String,String>();
        props.put("accessKey",amazonAccessKeyValue);
        props.put("secretKey",amazonSecretKeyValue);
        
        Set<String> libPaths = cpBuilder.getScanPaths(classList);
        
        factory = new EntityManagerFactoryImpl("RGSPersistenceUnit", props, libPaths); 
   
    }
    
    /**
     * This method is invoked by the Restful resource to initiate the user 
     * registration process.  This method returns the OAuth authorization URL, 
     * which the user must then use to retrieve (out of band) a verification 
     * token.
     * 
     * @param twitterScreenName
     * 
     * @return the authorization URL
     * 
     * todo: a twitter screen name can be re-associated with another user. 
     *          should really be keying off of the unique user id
     */
    @Override
    public String requestRegistration(String twitterScreenName) {
        logger.log(Level.FINE, "requestRegistration");
        
        String authorizationURL;
        Token requestToken;
        
        UserCredential user = new UserCredential();
        
        user.setTwitterScreenName(twitterScreenName);
        requestToken = oauthHandler.getRequestToken();
        user.setTwitterRequestToken(requestToken.getToken());
        user.setTwitterRequestSecret(requestToken.getSecret());
        //get rid of old records for this user
        deleteRecords(twitterScreenName);
        storeUserCredential(user);
        
        authorizationURL = oauthHandler.getAuthorizationURL(requestToken);
        
        return authorizationURL; 
    }
    
    /**
     * Once the user has retrieved the verification token using the
     * authorization token we gave them, they must return it to us.  This method
     * is called by the Restful resource to path the token through.  Using this
     * token, we then go back to twitter and retrieve the access token.  Once
     * we have the access token, we store it in SimpleDB for future use.
     * 
     * @param twitterScreenName
     * @param verificationToken
     */
    @Override
    public void setVerificationToken(String twitterScreenName, String verificationToken) {
        
        logger.log(Level.FINE, "setVerificationToken");
        
        //get the user's data from SimpleDB
        UserCredential user = retrieveUserCredential(twitterScreenName);
        
        logger.log(Level.FINEST, "Updating user: {0}", user.getTwitterScreenName());
        
        //Contact Twitter and exchange the verification token for the access token
        Token requestToken = OAuthUtilities.createToken(user.getTwitterRequestToken(), user.getTwitterRequestSecret());
        Token accessToken = oauthHandler.getAccessToken(requestToken, verificationToken);
        
        logger.log(Level.FINEST, "Store token: {0}", accessToken.getToken());
        
        //Update the user object with the access token and store it back to SimpleDB
        user.setTwitterAccessToken(accessToken.getToken());
        user.setTwitterAccessSecret(accessToken.getSecret());
        storeUserCredential(user);
    }
         
    /**
     * Stores the user credential to SimpleDB
     * @param user 
     */
    private void storeUserCredential(UserCredential user) {
                
        logger.log(Level.FINE, "Storing User Credential to SimpleDB");
        
        EntityManager em = factory.createEntityManager();

        logger.log(Level.FINEST, "Begin Storing User: {0}", user.getTwitterScreenName());
        em.persist(user);
        logger.log(Level.FINEST, "Done Storing User: {0}", user.getTwitterScreenName());
        
        em.close();
    }
    
    /**
     * 
     * @param twitterScreenName 
     */
    private void deleteRecords(String twitterScreenName) {
        logger.log(Level.FINE, "Deleting duplicate records for user: {0}", twitterScreenName);
        
        EntityManager em = factory.createEntityManager();
        List<UserCredential> results = getUC(twitterScreenName);
        
        logger.log(Level.FINER, "Got {0} Results from SimpleDB", results.size());
        
        //if we got results, then we have duplicate records
        for(UserCredential user : results) {
            em.remove(user);
        }
        em.close();
    }
    
    /**
     * 
     * @param twitterScreenName
     * @return 
     */
    private UserCredential retrieveUserCredential(String twitterScreenName) {
        logger.log(Level.FINE, "Retrieving User Credential from SimpleDB for user: {0}", twitterScreenName);
        
        UserCredential user = null;
        
        List<UserCredential> results = getUC(twitterScreenName);
        logger.log(Level.FINEST, "Got {0} Results from SimpleDB", results.size());

        //We shouldn't have duplicat records, but if we do, just return the first one
        if(results.size() > 0) {
            user = results.get(0);
            logger.log(Level.FINEST, "Retrieved User: {0} from SimpleDB", user.getTwitterScreenName());
        }
        
        return user;      
    } 
    
    /**
     * Retrieves the users matching the screen name from SimpleDB
     * 
     * @param twitterScreenName
     * @return 
     */
    private List<UserCredential> getUC(String twitterScreenName) {
        //get the user credential that corresponds to the twitter screen name
        EntityManager em = factory.createEntityManager();
        Query query = em.createQuery("select M from UserCredential M where M.twitterScreenName = :twitterScreenName");
        query.setParameter("twitterScreenName", twitterScreenName);
        List<UserCredential> results = query.getResultList();
        
        logger.log(Level.FINEST, "Got {0} Results from SimpleDB for user: {1}", new Object[]{results.size(), twitterScreenName});
        
        em.close();
        
        return results;
    }
    
    
}
