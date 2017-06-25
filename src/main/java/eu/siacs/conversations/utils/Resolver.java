package eu.siacs.conversations.utils;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.measite.minidns.DNSClient;
import de.measite.minidns.DNSName;
import de.measite.minidns.hla.DnssecResolverApi;
import de.measite.minidns.hla.ResolverResult;
import de.measite.minidns.record.A;
import de.measite.minidns.record.AAAA;
import de.measite.minidns.record.InternetAddressRR;
import de.measite.minidns.record.SRV;
import eu.siacs.conversations.Config;

public class Resolver {

    private static final String DIRECT_TLS_SERVICE = "_xmpps-client";
    private static final String STARTTLS_SERICE = "_xmpp-client";




    public static void registerLookupMechanism(Context context) {
        DNSClient.addDnsServerLookupMechanism(new AndroidUsingLinkProperties(context));
    }

    public static List<Result> resolve(String domain) {
        List<Result> results = new ArrayList<>();
        try {
            results.addAll(resolveSrv(domain,true));
        } catch (Throwable t) {
            Log.d(Config.LOGTAG,Resolver.class.getSimpleName()+": "+t.getMessage());
        }
        try {
            results.addAll(resolveSrv(domain,false));
        } catch (Throwable t) {
            Log.d(Config.LOGTAG,Resolver.class.getSimpleName()+": "+t.getMessage());
        }
        if (results.size() == 0) {
            results.add(Result.createDefault(domain));
        }
        Collections.sort(results);
        return results;
    }

    private static List<Result> resolveSrv(String domain, final boolean directTls) throws IOException {
        DNSName dnsName = DNSName.from((directTls ? DIRECT_TLS_SERVICE : STARTTLS_SERICE)+"._tcp."+domain);
        ResolverResult<SRV> result = DnssecResolverApi.INSTANCE.resolveDnssecReliable(dnsName,SRV.class);
        List<Result> results = new ArrayList<>();
        for(SRV record : result.getAnswersOrEmptySet()) {
            boolean added = results.addAll(resolveIp(record,A.class,result.isAuthenticData(),directTls));
            added |= results.addAll(resolveIp(record,AAAA.class,result.isAuthenticData(),directTls));
            if (!added) {
                Result resolverResult = Result.fromRecord(record, directTls);
                resolverResult.authenticated = resolverResult.isAuthenticated();
                results.add(resolverResult);
            }
        }
        return results;
    }

    private static <D extends InternetAddressRR> List<Result> resolveIp(SRV srv, Class<D> type, boolean authenticated, boolean directTls) {
        List<Result> list = new ArrayList<>();
        try {
            ResolverResult<D> results = DnssecResolverApi.INSTANCE.resolveDnssecReliable(srv.name, type);
            for (D record : results.getAnswersOrEmptySet()) {
                Result resolverResult = Result.fromRecord(srv, directTls);
                resolverResult.authenticated = results.isAuthenticData() && authenticated;
                resolverResult.ip = record.getInetAddress();
                list.add(resolverResult);
            }
        } catch (Throwable t) {
            Log.d(Config.LOGTAG,Resolver.class.getSimpleName()+": error resolving "+type.getSimpleName()+" "+t.getMessage());
        }
        return list;
    }

    public static class Result implements Comparable<Result> {
        private InetAddress ip;
        private DNSName hostname;
        private int port = 5222;
        private boolean directTls = false;
        private boolean authenticated =false;
        private int priority;

        public InetAddress getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }

        public DNSName getHostname() {
            return hostname;
        }

        public boolean isDirectTls() {
            return directTls;
        }

        public boolean isAuthenticated() {
            return authenticated;
        }

        @Override
        public String toString() {
            return "Result{" +
                    "ip='" + (ip==null?null:ip.getHostAddress()) + '\'' +
                    ", hostame='" + hostname.toString() + '\'' +
                    ", port=" + port +
                    ", directTls=" + directTls +
                    ", authenticated=" + authenticated +
                    ", priority=" + priority +
                    '}';
        }

        @Override
        public int compareTo(@NonNull Result result) {
            if (result.priority == priority) {
                if (directTls == result.directTls) {
                    return 0;
                } else {
                    return directTls ? 1 : -1;
                }
            } else {
                return priority - result.priority;
            }
        }

        public static Result fromRecord(SRV srv, boolean directTls) {
            Result result = new Result();
            result.port = srv.port;
            result.hostname = srv.name;
            result.directTls = directTls;
            result.priority = srv.priority;
            return result;
        }

        public static Result createDefault(String domain) {
            Result result = new Result();
            result.port = 5222;
            result.hostname = DNSName.from(domain);
            return result;
        }
    }

}
