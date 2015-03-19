/**
  *name    : Xuanli He
  *number  : 646502
  *program : SyncClient
  *version : 1.0
  *date    : 2014-09-10
  */
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class Client {

	/**
	 * @param args
	 */
    @Option(name="-file", usage="get file")
    private String filename;

    @Option(name="-host", usage="get host name")
    private String hostname = "localhost";

    @Option(name="-p", usage="get server's port")
    private int port = 4144;

    @Option(name="-b", usage="get blocksize")
    private int blocksize = 1024;

    @Option(name="-d", usage="get direction of transmission")
    private String dir = "push";

    public DatagramSocket socket;
    private InetAddress host;
    // Send negotiation message to server
    public void negotiation(){

        // Construct a socket
        //DatagramSocket socket = null;
        try{
            socket = new DatagramSocket();
            host = InetAddress.getByName(hostname);
        }
        catch(SocketException e){
            e.printStackTrace();
        }
        catch(Exception e){
            e.printStackTrace();
        }
        
        // Send negotiation message to server
        JSONObject obj = new JSONObject();
        obj.put("type", "negotiation");
        obj.put("blocksize", blocksize);
        obj.put("direction", dir);

        String msg = obj.toJSONString();

        try{
            byte[] buf = msg.getBytes();
            DatagramPacket request = new DatagramPacket(buf, buf.length, 
                    host,
                    port);
            socket.send(request);
            System.out.println("Sending: " + msg);
		} catch (SocketException e){
			e.printStackTrace();
		} catch (IOException e){
			e.printStackTrace();
		} 
    }
   
    // Capture ctrl-c
    private class ExitHanlder extends Thread{
        private DatagramSocket socket;
        private int port;
        private InetAddress host;

        ExitHanlder(DatagramSocket s, int p,
                InetAddress h){
            socket = s;
            port = p;
            host = h;
        }

        @Override
        public void run(){
            // Send negotiation message to server
            JSONObject obj = new JSONObject();
            obj.put("type", "shutdown");

            String msg = obj.toJSONString();

            try{
                byte[] buf = msg.getBytes();
                DatagramPacket request = new DatagramPacket(buf, buf.length, 
                        host,
                        port);
                socket.send(request);
            } catch (SocketException e){
                e.printStackTrace();
            } catch (IOException e){
                e.printStackTrace();
            } 
        }
    }

	public static void main(String[] args) {
        new Client().doMain(args);
    }

    public void doMain(String[] args){
        // Parse cmdline arguments
        CmdLineParser parser = new CmdLineParser(this);
        try{
            parser.parseArgument(args);
        }catch (CmdLineException e){
            System.err.println(e.getMessage());
            System.exit(-1);
        }

		SynchronisedFile file=null;
        
        //System.out.println("port: " + port);
        //System.out.println("blocksize: " + blocksize);
        //System.out.println("dir: " + dir);
        negotiation();
        Runtime.getRuntime().addShutdownHook(new ExitHanlder(socket,
                    port, host));
		/*
		 * Initialise the SynchronisedFiles.
		 */
		try {
			file=new SynchronisedFile(filename, blocksize);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}

		/*
		 * Start a thread to service the Instruction queue.
		 */
        if (dir.equals("push")) {
            // Sleep 5 seconds to wait server's
            // response
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(-1);
			}
            Thread sct = new Thread(new SyncClientThread(file, port,
                        socket, host));
            sct.start();
            /*
             * Continue forever, checking the fromFile every 5 seconds.
             */
            while(true){
                try {
                    // TODO: skip if the file is not modified
                    System.err.println("SyncClient: calling fromFile.CheckFileState()");
                    file.CheckFileState();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(-1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
        }
        else {
            Thread sst = new Thread(new SyncServerThread(file, socket));
            sst.start();
        }
		
	}

}
