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
package com.netscape.cmscore.dbs;

import java.util.Enumeration;
import java.util.Vector;

import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.dbs.DBAttrMapper;
import com.netscape.certsrv.dbs.IDBObj;

import netscape.ldap.LDAPAttribute;
import netscape.ldap.LDAPAttributeSet;

/**
 * A class represents ann attribute mapper that maps
 * a Java byte array object into LDAP attribute,
 * and vice versa.
 *
 * @author thomask
 * @version $Revision$, $Date$
 */
public class ByteArrayMapper extends DBAttrMapper {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ByteArrayMapper.class);

    private String mLdapName = null;
    private Vector<String> v = new Vector<String>();

    /**
     * Constructs a byte array mapper.
     */
    public ByteArrayMapper(String ldapName) {
        mLdapName = ldapName;
        v.addElement(mLdapName);
    }

    /**
     * Lists a list of supported ldap attribute names.
     */
    public Enumeration<String> getSupportedLDAPAttributeNames() {
        return v.elements();
    }

    /**
     * Maps object to ldap attribute set.
     */
    public void mapObjectToLDAPAttributeSet(IDBObj parent,
            String name, Object obj, LDAPAttributeSet attrs)
            throws EBaseException {

        byte[] data = (byte[]) obj;

        logger.debug("ByteArrayMapper: Mapping " + name + " to " + mLdapName);
        attrs.add(new LDAPAttribute(mLdapName, data));
    }

    /**
     * Maps LDAP attributes into object, and put the object
     * into 'parent'.
     */
    public void mapLDAPAttributeSetToObject(LDAPAttributeSet attrs,
            String name, IDBObj parent) throws EBaseException {
        LDAPAttribute attr = attrs.getAttribute(mLdapName);

        if (attr == null)
            return;
        parent.set(name, attr.getByteValues().nextElement());
    }

    /**
     * Maps search filters into LDAP search filter.
     */
    public String mapSearchFilter(String name, String op,
            String value) throws EBaseException {
        return mLdapName + op + value;
    }
}
