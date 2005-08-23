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

package com.zimbra.cs.service.mail;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.Address;
import javax.mail.MessagingException;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.property.Attendee;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.mailbox.Appointment;
import com.zimbra.cs.mailbox.*;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.service.Element;
import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.service.util.ParsedItemID;
import com.zimbra.cs.stats.StopWatch;
import com.zimbra.soap.ZimbraContext;



public class ModifyAppointment extends CalendarRequest 
{
    private static Log sLog = LogFactory.getLog(ModifyAppointment.class);
    private static StopWatch sWatch = StopWatch.getInstance("ModifyAppointment");
    
    // very simple: generate a new UID and send a REQUEST
    protected static class ModifyAppointmentParser implements ParseMimeMessage.InviteParser {
        protected Mailbox mmbox;
        protected Invite mInv;
        
        ModifyAppointmentParser(Mailbox mbox, Invite inv) {
            mmbox = mbox;
            mInv = inv;
        }
        
        public ParseMimeMessage.InviteParserResult parseInviteElement(OperationContext octxt, Account account, Element inviteElem) throws ServiceException {
            List atsToCancel = new ArrayList();

            ParseMimeMessage.InviteParserResult toRet = CalendarUtils.parseInviteForModify(account, inviteElem, mInv, atsToCancel);

            // send cancellations to any invitees who have been removed...
            updateRemovedInvitees(octxt, account, mmbox, mInv, atsToCancel);
            
            return toRet;
        }
    };

    
    public Element handle(Element request, Map context) throws ServiceException {
        long startTime = sWatch.start();
        try {
            ZimbraContext lc = getZimbraContext(context);
            Account acct = getRequestedAccount(lc);
            Mailbox mbox = getRequestedMailbox(lc);
            OperationContext octxt = lc.getOperationContext();

            ParsedItemID pid = ParsedItemID.Parse(request.getAttribute("id"));
            int compNum = (int)request.getAttributeLong("comp", 0);
            sLog.info("<ModifyAppointment id="+pid+" comp="+compNum+">");
            
            synchronized(mbox) {
                Appointment appt = mbox.getAppointmentById(pid.getItemIDInt()); 
                Invite inv = appt.getInvite(pid.getSubIdInt(), compNum);
                
                if (inv.hasRecurId()) {
                    throw ServiceException.INVALID_REQUEST("Called ModifyAppointmentException on invite "+inv.getMailItemId()+
                            "with RECURRENCE_ID != 0", null);
                }

                // response
                Element response = lc.createElement(MailService.MODIFY_APPOINTMENT_RESPONSE);
                
                return modifyAppointment(octxt, request, acct, mbox, inv, response);
            } // synchronized on mailbox                
        } finally {
            sWatch.stop(startTime);
        }        
    }
    
    protected static Element modifyAppointment(OperationContext octxt, Element request, Account acct, Mailbox mbox,
            Invite inv, Element response) throws ServiceException
    {
        // <M>
        Element msgElem = request.getElement(MailService.E_MSG);
        
        ModifyAppointmentParser parser = new ModifyAppointmentParser(mbox, inv);
        
        CalSendData dat = handleMsgElement(octxt, msgElem, acct, mbox, parser);
        
        // If we are sending this update to other people, then we MUST be the organizer!
        if (!inv.thisAcctIsOrganizer(acct)) {
            try {
                Address[] rcpts = dat.mMm.getAllRecipients();
                if (rcpts != null && rcpts.length > 0) {
                    throw MailServiceException.MUST_BE_ORGANIZER("ModifyAppointment");
                }
            } catch (MessagingException e) {
                throw ServiceException.FAILURE("Checking recipients of outgoing msg ", e);
            }
        }

        sendCalendarMessage(octxt, acct, mbox, dat, response);

        return response;        
    }
    
    protected static void updateRemovedInvitees(OperationContext octxt, Account acct, Mailbox mbox, Invite inv, List toCancel)
    throws ServiceException {
        if (!inv.thisAcctIsOrganizer(acct)) {
            // we ONLY should update the removed attendees if we are the organizer!
            return;
        }
        
        CalSendData dat = new CalSendData();
        dat.mSaveToSent = shouldSaveToSent(acct);
        dat.mOrigId = inv.getMailItemId();
        dat.mReplyType = TYPE_REPLY;

        String text = "You have been removed from the Attendee list by the organizer";
        String subject = "CANCELLED: " + inv.getName();
        
        for (Iterator cancelIter = toCancel.iterator(); cancelIter.hasNext(); ) {
            Attendee cancelAt = (Attendee)cancelIter.next();
            
            if (sLog.isDebugEnabled()) {
                sLog.debug("Sending cancellation message \"" + subject + "\" to " +
                           cancelAt.getCalAddress().toString());
            }
            
            Calendar cal = CalendarUtils.buildCancelInviteCalendar(acct, inv, text, cancelAt);
            
            dat.mMm = CalendarUtils.createDefaultCalendarMessage(acct, 
                    cancelAt.getCalAddress(), subject, text, inv.getUid(), cal);
            
            sendCalendarMessage(octxt, acct, mbox, dat, null); 
        }
    }
}
