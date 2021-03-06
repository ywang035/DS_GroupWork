package unimelb.bitbox;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.HostPort;

public class Peer extends Thread
{
    static protected ServerMain mainServer;
    private static Logger log = Logger.getLogger(Peer.class.getName());
    protected  Synchronize syn;
    public static void main( String[] args ) throws IOException, NumberFormatException, NoSuchAlgorithmException, InterruptedException
    {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tc] %2$s %4$s: %5$s%n");
        log.info("BitBox Peer starting...");
        Configuration.getConfiguration();

        mainServer = new ServerMain();
        mainServer.peer.syn = new Synchronize(mainServer);

    }

    private int port =  Integer.parseInt(Configuration.getConfigurationValue("port"));
    private int udpPort = Integer.parseInt(Configuration.getConfigurationValue("udpPort"));
    private String [] peerstring = Configuration.getConfigurationValue("peers").split(" ");
    private ArrayList<String> peers = new ArrayList<String>(Arrays.asList(peerstring));
    protected ArrayList<HostPort> peerHosts = new ArrayList<>();

    private String mode = Configuration.getConfigurationValue("mode");

    protected ArrayList<TCPclient> clientList = new ArrayList<>();
    private TCPserver TCPserver;
    protected UDPclient UDPclient;
    private UDPserver UDPserver;
    private int length;

    public void run(){

        for (String i : peers) {
            if(i.contains(":"))
                peerHosts.add(new HostPort(i));
        }
        peers.clear();
        length = peerHosts.size();

        if (mode.equals("tcp")) {
            TCPserver = new TCPserver(port, this);
            TCPserver.start();

        } else {
            UDPserver = new UDPserver(udpPort, this);
            UDPserver.start();
        }

        while(true){

            if (mode.equals("tcp")) {
                System.out.println("Peers suppose to be online"+length+" Peers Actual Online "+TCPserver.serverlist.size());
                if (length != TCPserver.serverlist.size()){

                    System.out.println("start a connecting to other peers");

                    peerHosts.removeAll(TCPserver.serverlist);
                    peerHosts.addAll(TCPserver.serverlist);

                    for (HostPort hostport : peerHosts) {
                        if(!peers.contains(hostport.host)) {
                            TCPclient client = new TCPclient(hostport, this);
                            clientList.add(client);
                            peers.add(client.ip);
                            if (!peerHosts.equals("")) {
                                client.start();
                            }
                        }
                    }
                    length = TCPserver.serverlist.size();
                    if(TCPserver.serverlist.size()>0&&clientList.size()>0){
                        if(!syn.isAlive()){
                            syn.start();
                            System.out.println("Connected to the peer");
                            System.out.println("Synchronize service start");
                        }
                    }
                }


            } else {
                System.out.println("Peers supposed to be online"+length+" Actual Online peers"+UDPserver.onlinePeers.size());
                if (length != UDPserver.onlinePeers.size()) {

                    peerHosts.removeAll(UDPserver.onlinePeers);
                    peerHosts.addAll(UDPserver.onlinePeers);
                    if (!peerstring[0].equals("") && UDPclient == null) {
                        UDPclient = new UDPclient(peerHosts, this);
                        UDPclient.start();
                    } else if (UDPclient != null) {
                        peerHosts.removeAll(UDPclient.onlinePeers);
                        for (HostPort hostport : peerHosts) {
                            UDPclient.handShake(hostport);
                        }
                    }
                    length = UDPserver.onlinePeers.size();
                }
                if(UDPclient.onlinePeers.size()>0&&UDPserver.onlinePeers.size()>0){
                    if(!syn.isAlive()){
                        syn.start();
                        System.out.println("Connected to the peer");
                        System.out.println("Synchronize service start");
                    }
                }
            }
            try {
                Thread.sleep(5*1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        //configuration into peerHost
    }

    public  void Broadcast(String message){

        if(mode.equals("tcp")){
            for(TCPclient t:clientList) {
                t.sendtoServer(message);
            }
        }
        else{
            for(HostPort hostpot:UDPclient.onlinePeers) {
                UDPclient.sendToServer(hostpot.host,message);
            }
        }

    }

    public void clientToServer(String ip,String message){
        if(mode.equals("tcp")){
            for(TCPclient t:clientList) {
                if(t.ip .equals(ip.replace("localhost",""))) {
                    t.sendtoServer(message);
                }
            }
        }
        else{
            UDPclient.sendToServer(ip,message);
        }
    }

}