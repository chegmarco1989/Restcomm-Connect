/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */
package org.restcomm.connect.core.service.profile;

import java.net.URI;
import java.sql.SQLException;

import javax.ws.rs.core.UriInfo;

import org.apache.log4j.Logger;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Profile;
import org.restcomm.connect.dao.entities.ProfileAssociation;

import com.sun.jersey.core.header.LinkHeader;
import com.sun.jersey.core.header.LinkHeader.LinkHeaderBuilder;
import org.restcomm.connect.core.service.api.ProfileService;

public class ProfileServiceImpl implements ProfileService {
    private static Logger logger = Logger.getLogger(ProfileServiceImpl.class);

    private static String DEFAULT_PROFILE_SID = Profile.DEFAULT_PROFILE_SID;
    private static final String PROFILE_REL_TYPE = "related";
    private static final String TITLE_PARAM = "title";

    private final DaoManager daoManager;

    public ProfileServiceImpl(DaoManager daoManager) {
        super();
        this.daoManager = daoManager;
    }

    /**
     * @param accountSid
     * @return will return associated profile of provided accountSid
     */
    @Override
    public Profile retrieveEffectiveProfileByAccountSid(String accountSid) {
        Profile profile = null;
        Sid currentAccount = new Sid(accountSid);
        Account lastAccount = null;

        // try to find profile in account hierarchy
        do {
            profile = retrieveExplicitlyAssociatedProfile(currentAccount.toString());
            if (profile == null) {
                lastAccount = daoManager.getAccountsDao().getAccount(currentAccount);
                if (lastAccount != null) {
                    currentAccount = lastAccount.getParentSid();
                } else {
                    throw new RuntimeException("account not found!!!");
                }
            }
        } while (profile == null && currentAccount != null);

        // if profile is not found in account hierarchy,try org
        if (profile == null && lastAccount != null) {
            Sid organizationSid = lastAccount.getOrganizationSid();
            profile = retrieveExplicitlyAssociatedProfile(organizationSid.toString());
        }

        // finally try with default profile
        if (profile == null) {
            try {
                profile = daoManager.getProfilesDao().getProfile(DEFAULT_PROFILE_SID);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Returning profile:" + profile);
        }

        return profile;
    }

    /**
     * @param organizationSid
     * @return will return associated profile of provided organization sid
     */
    public Profile retrieveEffectiveProfileByOrganizationSid(String organizationSid) {
        Profile profile = null;
        profile = retrieveExplicitlyAssociatedProfile(organizationSid.toString());

        // finally try with default profile
        if (profile == null) {
            try {
                profile = daoManager.getProfilesDao().getProfile(DEFAULT_PROFILE_SID);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Returning profile:" + profile);
        }
        return profile;
    }

    /**
     * @param targetSid
     * @return will return associated profile of provided target (account or
     *         organization)
     */
    @Override
    public Profile retrieveExplicitlyAssociatedProfile(String targetSid) {
        ProfileAssociation assoc = daoManager.getProfileAssociationsDao().getProfileAssociationByTargetSid(targetSid);
        Profile profile = null;
        if (assoc != null) {
            try {
                profile = daoManager.getProfilesDao().getProfile(assoc.getProfileSid().toString());
            } catch (SQLException ex) {
                throw new RuntimeException("problem retrieving profile", ex);
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Returning profile:" + profile);
        }
        return profile;
    }

    /**
     * @param targetSid
     * @param info
     * @param resource
     * @return
     */
    @Override
    public LinkHeader composeProfileLink(String targetSid, UriInfo info, Class resource) {
        String sid = targetSid.toString();
        URI uri = info.getBaseUriBuilder().path(resource).path(sid).build();
        LinkHeaderBuilder link = LinkHeader.uri(uri).parameter(TITLE_PARAM, "Profiles");
        return link.rel(PROFILE_REL_TYPE).build();
    }
}
