/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2007, 2009, 2010, 2011, 2012, 2013, 2014, 2016 Synacor, Inc.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.imap;

import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;

import com.google.common.collect.ImmutableMap;
import com.zimbra.common.localconfig.LC;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.stats.RealtimeStatsCallback;
import com.zimbra.common.util.Log;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.server.NioConnection;
import com.zimbra.cs.server.NioHandler;
import com.zimbra.cs.server.NioServer;
import com.zimbra.cs.server.ServerThrottle;
import com.zimbra.cs.stats.ZimbraPerf;
import com.zimbra.cs.util.EhcacheManager;

public final class NioImapServer extends NioServer implements ImapServer, RealtimeStatsCallback {
    private final NioImapDecoder decoder;

    public NioImapServer(ImapConfig config) throws ServiceException {
        super(config);
        decoder = new NioImapDecoder();
        decoder.setMaxChunkSize(config.getWriteChunkSize());
        decoder.setMaxLineLength(config.getMaxRequestSize());
        decoder.setMaxLiteralSize(config.getMaxMessageSize());
        registerMBean(getName());
        ZimbraPerf.addStatsCallback(this);
        ServerThrottle.configureThrottle(config.getProtocol(), LC.imap_throttle_ip_limit.intValue(), LC.imap_throttle_acct_limit.intValue(), getThrottleSafeHosts(), getThrottleWhitelist());
    }

    @Override
    public String getName() {
        return config.isSslEnabled() ? "ImapSSLServer" : "ImapServer";
    }

    @Override
    public NioHandler createHandler(NioConnection conn) {
        return new NioImapHandler(this, conn);
    }

    @Override
    protected ProtocolCodecFactory getProtocolCodecFactory() {
        return new ProtocolCodecFactory() {
            @Override
            public ProtocolEncoder getEncoder(IoSession session) throws Exception {
                return DEFAULT_ENCODER;
            }

            @Override
            public ProtocolDecoder getDecoder(IoSession session) {
                return decoder;
            }
        };
    }

    @Override
    public ImapConfig getConfig() {
        return (ImapConfig) super.getConfig();
    }

    @Override
    public Log getLog() {
        return ZimbraLog.imap;
    }

    @Override
    public Map<String, Object> getStatData() {
        String connStatName = getConfig().isSslEnabled() ? ZimbraPerf.RTS_IMAP_SSL_CONN : ZimbraPerf.RTS_IMAP_CONN;
        String threadStatName = getConfig().isSslEnabled() ? ZimbraPerf.RTS_IMAP_SSL_THREADS : ZimbraPerf.RTS_IMAP_THREADS;
        return ImmutableMap.of(connStatName, (Object) getNumConnections(), threadStatName, getNumThreads());
    }

    protected static SSLContext initSSLContext() throws Exception {
        // If running inside mailboxd, use default SSL context initialization, else
        // provide the IMAP keystore path and password to the init code.
        if (System.getProperty(ImapDaemon.IMAP_SERVER_EMBEDDED, "true").equals("false")) {
            return NioServer.initSSLContext();
        } else {
            return initSSLContext(LC.imapd_keystore.value(), LC.imapd_keystore_password.value());
        }
    }

}
