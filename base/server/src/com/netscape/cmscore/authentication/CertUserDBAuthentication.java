// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2007 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---
package com.netscape.cmscore.authentication;

import java.security.cert.X509Certificate;

import org.dogtagpki.server.authentication.AuthManager;
import org.dogtagpki.server.authentication.AuthManagerConfig;
import org.dogtagpki.server.authentication.AuthToken;
import org.dogtagpki.server.authentication.AuthenticationConfig;
import org.mozilla.jss.netscape.security.x509.X509CertImpl;

import com.netscape.certsrv.authentication.EInvalidCredentials;
import com.netscape.certsrv.authentication.EMissingCredential;
import com.netscape.certsrv.authentication.IAuthCredentials;
import com.netscape.certsrv.authentication.IAuthToken;
import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.IConfigStore;
import com.netscape.certsrv.usrgrp.CertUserLocator;
import com.netscape.certsrv.usrgrp.Certificates;
import com.netscape.certsrv.usrgrp.EUsrGrpException;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.apps.CMSEngine;
import com.netscape.cmscore.usrgrp.ExactMatchCertUserLocator;
import com.netscape.cmscore.usrgrp.User;

/**
 * Certificate server agent authentication.
 * Maps a SSL client authenticate certificate to a user (agent) entry in the
 * internal database.
 * <P>
 *
 * @author lhsiao
 * @author cfu
 * @version $Revision$, $Date$
 */
public class CertUserDBAuthentication implements AuthManager {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CertUserDBAuthentication.class);

    /* result auth token attributes */
    public final static String TOKEN_USERDN = "user";
    public final static String TOKEN_USER_DN = "userdn";
    public final static String TOKEN_USERID = "userid";
    public final static String TOKEN_UID = "uid";

    /* required credentials */
    public final static String CRED_CERT = AuthManager.CRED_SSL_CLIENT_CERT;

    /* required credentials */
    protected String[] mRequiredCreds = { CRED_CERT };

    /* config parameters to pass to console (none) */
    protected static String[] mConfigParams = null;

    private String mName = null;
    private String mImplName = null;
    private AuthenticationConfig authenticationConfig;
    private AuthManagerConfig mConfig;

    private CertUserLocator mCULocator = null;

    private boolean mRevocationCheckingEnabled = false;
    private IConfigStore mRevocationChecking = null;

    public CertUserDBAuthentication() {
    }

    public AuthenticationConfig getAuthenticationConfig() {
        return authenticationConfig;
    }

    public void setAuthenticationConfig(AuthenticationConfig authenticationConfig) {
        this.authenticationConfig = authenticationConfig;
    }

    /**
     * initializes the CertUserDBAuthentication auth manager
     * <p>
     * called by AuthSubsystem init() method, when initializing all available authentication managers.
     *
     * @param owner - The authentication subsystem that hosts this
     *            auth manager
     * @param config - The configuration store used by the
     *            authentication subsystem
     */
    public void init(String name, String implName, AuthManagerConfig config)
            throws EBaseException {
        mName = name;
        mImplName = implName;
        CMSEngine engine = CMS.getCMSEngine();
        mConfig = config;

        if (authenticationConfig != null) {
            mRevocationChecking = authenticationConfig.getSubStore("revocationChecking");
        }
        if (mRevocationChecking != null) {
            mRevocationCheckingEnabled = mRevocationChecking.getBoolean("enabled", false);
            if (mRevocationCheckingEnabled) {
                int size = mRevocationChecking.getInteger("bufferSize", 0);
                long interval = mRevocationChecking.getInteger("validityInterval", 28800);
                long unknownStateInterval = mRevocationChecking.getInteger("unknownStateInterval", 1800);

                if (size > 0)
                    engine.setListOfVerifiedCerts(size, interval, unknownStateInterval);
            }
        }

        mCULocator = new ExactMatchCertUserLocator();
    }

    /**
     * Gets the name of this authentication manager.
     */
    public String getName() {
        return mName;
    }

    /**
     * Gets the plugin name of authentication manager.
     */
    public String getImplName() {
        return mImplName;
    }

    /**
     * authenticates user(agent) by certificate
     * <p>
     * called by other subsystems or their servlets to authenticate users (agents)
     *
     * @param authCred - authentication credential that contains
     *            an usrgrp.Certificates of the user (agent)
     * @return the authentication token that contains the following
     *
     * @exception com.netscape.certsrv.base.EAuthsException any
     *                authentication failure or insufficient credentials
     * @see org.dogtagpki.server.authentication.AuthToken
     * @see com.netscape.certsrv.usrgrp.Certificates
     */
    public IAuthToken authenticate(IAuthCredentials authCred)
            throws EMissingCredential, EInvalidCredentials, EBaseException {
        logger.debug("CertUserDBAuth: started");
        AuthToken authToken = new AuthToken(this);
        logger.debug("CertUserDBAuth: Retrieving client certificate");
        X509Certificate[] x509Certs =
                (X509Certificate[]) authCred.get(CRED_CERT);

        if (x509Certs == null) {
            logger.error("CertUserDBAuthentication: " + CMS.getLogMessage("CMSCORE_AUTH_MISSING_CERT"));
            throw new EMissingCredential(CMS.getUserMessage("CMS_AUTHENTICATION_NULL_CREDENTIAL", CRED_CERT));
        }
        logger.debug("CertUserDBAuth: Got client certificate");

        if (mRevocationCheckingEnabled) {
            X509CertImpl cert0 = (X509CertImpl) x509Certs[0];
            if (cert0 == null) {
                logger.error("CertUserDBAuthentication: " + CMS.getLogMessage("CMSCORE_AUTH_NO_CERT"));
                throw new EInvalidCredentials(CMS.getUserMessage("CMS_AUTHENTICATION_NO_CERT"));
            }
            CMSEngine engine = CMS.getCMSEngine();
            if (engine.isRevoked(x509Certs)) {
                logger.error("CertUserDBAuthentication: " + CMS.getLogMessage("CMSCORE_AUTH_REVOKED_CERT"));
                throw new EInvalidCredentials(CMS.getUserMessage("CMS_AUTHENTICATION_INVALID_CREDENTIAL"));
            }
        }

        logger.debug("Authentication: client certificate found");

        // map cert to user
        User user = null;
        Certificates certs = new Certificates(x509Certs);

        try {
            user = mCULocator.locateUser(certs);
        } catch (EUsrGrpException e) {
            logger.error("CertUserDBAuthentication: cannot map certificate to any user: " + e.getMessage(), e);
            logger.error("CertUserDBAuthentication: " + CMS.getLogMessage("CMSCORE_AUTH_AGENT_AUTH_FAILED", x509Certs[0].getSerialNumber()
                    .toString(16), x509Certs[0].getSubjectDN().toString(), e.toString()));
            throw new EInvalidCredentials(CMS.getUserMessage("CMS_AUTHENTICATION_INVALID_CREDENTIAL"));
        } catch (netscape.ldap.LDAPException e) {
            logger.error("CertUserDBAuthentication: " + CMS.getLogMessage("CMSCORE_AUTH_CANNOT_AGENT_AUTH", e.toString()), e);
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INTERNAL_ERROR", e.toString()));
        }

        // any unexpected error occurs like internal db down,
        // UGSubsystem only returns null for user.
        if (user == null) {
            logger.error("CertUserDBAuthentication: cannot map certificate to any user");
            logger.error("CertUserDBAuthentication: " + CMS.getLogMessage("CMSCORE_AUTH_AGENT_USER_NOT_FOUND"));
            throw new EInvalidCredentials(CMS.getUserMessage("CMS_AUTHENTICATION_INVALID_CREDENTIAL"));
        }

        logger.debug("Authentication: mapped certificate to user");

        authToken.set(TOKEN_USERDN, user.getUserDN());
        authToken.set(TOKEN_USER_DN, user.getUserDN());
        authToken.set(TOKEN_USERID, user.getUserID());
        authToken.set(TOKEN_UID, user.getUserID());
        authToken.set(CRED_CERT, certs);

        logger.info("CertUserDBAuthentication: " + CMS.getLogMessage("CMS_AUTH_AUTHENTICATED", user.getUserID()));

        return authToken;
    }

    /**
     * get the list of authentication credential attribute names
     * required by this authentication manager. Generally used by
     * the servlets that handle agent operations to authenticate its
     * users. It calls this method to know which are the
     * required credentials from the user (e.g. Javascript form data)
     *
     * @return attribute names in Vector
     */
    public String[] getRequiredCreds() {
        return (mRequiredCreds);
    }

    /**
     * get the list of configuration parameter names
     * required by this authentication manager. Generally used by
     * the Certificate Server Console to display the table for
     * configuration purposes. CertUserDBAuthentication is currently not
     * exposed in this case, so this method is not to be used.
     *
     * @return configuration parameter names in Hashtable of Vectors
     *         where each hashtable entry's key is the substore name, value is a
     *         Vector of parameter names. If no substore, the parameter name
     *         is the Hashtable key itself, with value same as key.
     */
    public String[] getConfigParams() {
        return (mConfigParams);
    }

    /**
     * prepare this authentication manager for shutdown.
     */
    public void shutdown() {
    }

    /**
     * gets the configuretion substore used by this authentication
     * manager
     *
     * @return configuration store
     */
    public AuthManagerConfig getConfigStore() {
        return mConfig;
    }
}
