/**
  *name    : Zodiac
  *program : SyncServer
  *version : 1.0
  *date    : 2014-09-10
  */
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.InetAddress;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class Server {

	/**
	 * @param args
	 */
    @Option(name="-file", usage="get file")
    private String filename;

    @Option(name="-p", usage="get server's port")
    private int port = 4144;

    // block size
    private int blocksize;
    private String dir;

    public DatagramSocket socket;
    private int clientPort;
    private InetAddress host;
    // Parse negotiation message
    public void parseMsg(int port){

        //DatagramSocket socket = null;
        try{
            socket = new DatagramSocket(port);
        }
        catch (SocketException e){
            e.printStackTrace();
        }

        // Construct a packet to receive message
        DatagramPacket reply = null;
        try{
            byte[] rbuf = new byte[2048];
            reply = new DatagramPacket(rbuf, rbuf.length);
            socket.receive(reply);
            clientPort = reply.getPort();
            host = reply.getAddress();
		} catch (SocketException e){
			e.printStackTrace();
		} catch (IOException e){
			e.printStackTrace();
		} 

        String msg = new String(reply.getData(), 0 , reply.getLength());
        System.out.println("Receive: " + msg);
        JSONObject obj = null;
		
        // Parse neogotiation message
        try {
            JSONParser parser = new JSONParser();
            obj = (JSONObject) parser.parse(msg);
        } catch (org.json.simple.parser.ParseException e) {
            e.printStackTrace();
            System.exit(-1);
        }
		if(obj!=null){
            dir = (String) obj.get("direction");
            blocksize = ((Long)obj.get("blocksize")).intValue();
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
        new Server().doMain(args);
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

        System.out.println("Server is running...");

        // Initialise the destination file
        //int port = Integer.parseInt(args[1]);
        parseMsg(port);

        // Capture ctrl-c
        Runtime.getRuntime().addShutdownHook(new ExitHanlder(socket,
                    clientPort, host));
		try {
			file=new SynchronisedFile(filename, blocksize);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		/*
		 * Start a thread to service the Instruction queue.
		 */
        if (dir.equals("push")){
            Thread sst = new Thread(new SyncServerThread(file,socket));
            sst.start();
        }
        else {
            Thread sct = new Thread(new SyncClientThread(file,clientPort,socket,
                        host));
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

	}

}
