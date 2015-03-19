/**
  *name    : Zodiac
  *program : SyncClient
  *version : 1.0
  *date    : 2014-09-10
  */
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.io.IOException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/*
 * This test thread provides comments to explain how the Client/Server 
 * architecture could be implemented that uses the file synchronisation protocol.
 */

public class SyncClientThread implements Runnable {

    // source file
	SynchronisedFile fromFile;
    // socket for communication with server
    private DatagramSocket socket;
    // server's IP
    private InetAddress host;
    // server's port
    private int port;
    // counter
    private int counter;
	
    // Constructor without port
	SyncClientThread(SynchronisedFile ff, int port,
            DatagramSocket socket, InetAddress hostname){
		fromFile=ff;
        host = hostname;
        //port = 2345;
        this.socket = socket;
        this.port = port;
        counter = -1;
	}

    // Preprocess file instruction
    private void preprocessInstruction(Instruction inst){
        // Startupdate instruction
        if (inst.Type().equals("StartUpdate")){
            startUpdate(inst);
        }
        // End update instruction
        else if (inst.Type().equals("EndUpdate")){
            endUpdate(inst);
        }
        // Copyblock instruction
        else if (inst.Type().equals("CopyBlock")){
            copyBlock(inst);
        }
		assert(false); // we should never get an unknown instruction
    
    }

    // Handle startupdate instruction
    private void startUpdate(Instruction inst){
        // Reset counter to 1
        counter = 1;
        // Construct json message
        JSONObject obj = new JSONObject(); 
        obj.put("type", "inst");
        obj.put("inst", fromString(inst.ToJSON()));
        //obj.put("inst", inst);
        //System.out.println(inst.ToJSON());
        obj.put("counter", counter);
        // Send instruction to server
		send(obj.toJSONString());
    }
    // Handle endupdate instruction
    private void endUpdate(Instruction inst){
        // Set counter to 0, and ends transition
        //counter = 0;
        counter++;
        // Construct json message
        JSONObject obj = new JSONObject(); 
        obj.put("type", "inst");
        obj.put("inst", fromString(inst.ToJSON()));
        //obj.put("inst", inst);
        obj.put("counter", counter);
        // Send instruction to server
		send(obj.toJSONString());
    }
    // Handle copyblock instruction
    private void copyBlock(Instruction inst){
        // Increment the counter
        counter++;
        // Construct json message
        JSONObject obj = new JSONObject(); 
        obj.put("type", "inst");
        obj.put("inst", fromString(inst.ToJSON()));
        //obj.put("inst", inst);
        obj.put("counter", counter);
        // Send instruction to server
		send(obj.toJSONString());
    }
    // Handle startupdate instruction
    private void newBlock(Instruction inst){
        Instruction upgraded=new NewBlockInstruction((CopyBlockInstruction)inst);
        // Construct json message
        JSONObject obj = new JSONObject(); 
        obj.put("type", "inst");
        obj.put("inst", fromString(upgraded.ToJSON()));
        //obj.put("inst", upgraded);
        obj.put("counter", counter);
        // Send instruction to server
        send(obj.toJSONString());
    }

    // Transfer string to json object
    private JSONObject fromString(String jsn){
        JSONObject obj = null;
		
        try {
            JSONParser parser = new JSONParser();
            System.out.println(jsn);
            obj = (JSONObject) parser.parse(jsn);
        } catch (org.json.simple.parser.ParseException e) {
            e.printStackTrace();
            //System.out.println(e.getUnexpectedObject());
            //System.out.println(e.getPosition());
            System.exit(-1);
        }

        return obj;
    }
    // Send file instruction to server
    private void send(String msg){
        
        try{
            byte[] buf = msg.getBytes();
            DatagramPacket request = new DatagramPacket(buf, buf.length, host, port);
            socket.send(request);
            //System.out.println("Sending: " + msg);
		} catch (SocketException e){
			e.printStackTrace();
		} catch (IOException e){
			e.printStackTrace();
		} 
    }

    // Receive file instruction from server
    private String receive(){
        DatagramPacket reply = null;
        try{
            byte[] rbuf = new byte[512];
            reply = new DatagramPacket(rbuf, rbuf.length);
            socket.receive(reply);
		} catch (SocketException e){
			e.printStackTrace();
		} catch (IOException e){
			e.printStackTrace();
		} 
        
        return new String(reply.getData(),0 , reply.getLength());
    }

    // Handle received message
    private void proprocessInstruction(String msg,
            Instruction inst){
        // Parse message
		JSONObject obj=null;
		
		try {
            JSONParser parser = new JSONParser();
			obj = (JSONObject) parser.parse(msg);
		} catch (org.json.simple.parser.ParseException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		if(obj!=null){
            String type = (String)obj.get("type");
            int counter = 1;
            //int counter = (int)(long)obj.get("counter");
            if (type.equals("shutdown")) {
                System.exit(0);
            }
            else
                counter = ((Long)obj.get("counter")).intValue();

            // Reply is excepting
            if (type.equals("expecting")){
               preprocessInstruction(inst); 
            }
            // Encounter Blockunavailableexception on server
            else if (type.equals("exception")){
                newBlock(inst);
                // Receive the status of new block
                String ack = receive();
                System.out.println("Receive: "+ ack);
            }
            // Reply is acknowledgement
            else if (type.equals("ack"))
                ;
		}
    }
	
	@Override
	public void run() {
		Instruction inst;
		// The Client reads instructions to send to the Server
		while((inst=fromFile.NextInstruction())!=null){

            // Preprocess instruction
            preprocessInstruction(inst);
            // Get status of sending package
            String msg = receive();
            System.out.println("Receive: "+ msg);
            // Handle received message
            proprocessInstruction(msg, inst);
		} // get next instruction loop forever
	}
}
