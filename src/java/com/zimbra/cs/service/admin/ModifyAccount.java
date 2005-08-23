/*
***** BEGIN LICENSE BLOCK *****
Version: ZPL 1.1

The contents of this file are subject to the Zimbra Public License
Version 1.1 ("License"); you may not use this file except in
compliance with the License. You may obtain a copy of the License at
http://www.zimbra.com/license

Software distributed under the License is distributed on an "AS IS"
basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
the License for the specific language governing rights and limitations
under the License.

The Original Code is: Zimbra Collaboration Suite.

The Initial Developer of the Original Code is Zimbra, Inc.  Portions
created by Zimbra are Copyright (C) 2005 Zimbra, Inc.  All Rights
Reserved.

Contributor(s): 

***** END LICENSE BLOCK *****
*/

/*
 * Created on Jun 17, 2004
 */
package com.zimbra.cs.service.admin;

import java.util.Map;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.service.Element;
import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.util.ZimbraLog;
import com.zimbra.soap.ZimbraContext;

/**
 * @author schemers
 */
public class ModifyAccount extends AdminDocumentHandler {

	public Element handle(Element request, Map context) throws ServiceException {

        ZimbraContext lc = getZimbraContext(context);
	    Provisioning prov = Provisioning.getInstance();

	    String id = request.getAttribute(AdminService.E_ID);
	    Map attrs = AdminService.getAttrs(request);

	    Account account = prov.getAccountById(id);
        if (account == null)
            throw AccountServiceException.NO_SUCH_ACCOUNT(id);

        // pass in true to checkImmutable
        account.modifyAttrs(attrs, true);

        ZimbraLog.security.info(ZimbraLog.encodeAttrs(
                new String[] {"cmd", "ModifyAccount","name", account.getName()}, attrs));

        Element response = lc.createElement(AdminService.MODIFY_ACCOUNT_RESPONSE);
        GetAccount.doAccount(response, account);
	    return response;
	}
}