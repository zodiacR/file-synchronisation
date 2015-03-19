/**
  *name    : Xuanli He
  *number  : 646502
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

public class SyncServerThread implements Runnable {

    // Destination file
	SynchronisedFile toFile; 
    private DatagramSocket socket;
    private int port;
    private int counter;
    private InetAddress host;
	
	SyncServerThread(SynchronisedFile tf,
            DatagramSocket socket){
		toFile=tf;
        // No instruction is received
        counter = 0;
        //port = 2345;
        this.socket = socket;
	}
	
    // Receive file instruction from server
    private String receive(){
        DatagramPacket reply = null;
        try{
            byte[] rbuf = new byte[4096];
            reply = new DatagramPacket(rbuf, rbuf.length);
            socket.receive(reply);
            port = reply.getPort();
            host = reply.getAddress();
		} catch (SocketException e){
			e.printStackTrace();
		} catch (IOException e){
			e.printStackTrace();
		} 
        
        return new String(reply.getData(), 0 , reply.getLength());
    }
    
    // Process message from client
    private void processMessage(String msg){
        
        //JSONObject obj = new JSONObject(msg);
        JSONObject obj = null;
		
        try {
            JSONParser parser = new JSONParser();
            System.out.println("Receive: " + msg);
            //System.out.println(msg.length());
            obj = (JSONObject) parser.parse(msg);
        } catch (org.json.simple.parser.ParseException e) {
            e.printStackTrace();
            System.exit(-1);
        }
		if(obj!=null){
            String type = (String) obj.get("type");
            JSONObject instruction = null;
            int counter = 1;
            // Receive shutdown signal
            if (type.equals("shutdown")) {
                System.exit(0);
            }
            else{
                instruction = (JSONObject) obj.get("inst");
                counter = ((Long)obj.get("counter")).intValue();
            }
            // process startupdate instruction, 
            // copyblock instruction and endupdate instruction
            if (counter == 1 | this.counter + 1 == counter){
                this.counter++;
                processInstruction(instruction.toJSONString());
                acknowledgement();

                type = (String)instruction.get("Type"); 
                if (type.equals("EndUpdate"))
                    this.counter = 0;
            }
            // endupdate instruction
            //else if (counter == 0){
                //this.counter = 0;
                //processInstruction(instruction.toJSONString());
                //acknowledgement();
            //}
            // Expecting number is wrong
            else{
                expecting();
            }
        }
    }

    // Acknowledgement
    private void acknowledgement()
    {
        // Construct a json message of acknowledgement
        JSONObject obj = new JSONObject();
        obj.put("type", "ack");
        obj.put("counter", this.counter);

        reply(obj.toJSONString());
    }

    // Expecting counter is wrong
    private void expecting()
    {
        // Construct a json message of expecting
        JSONObject obj = new JSONObject();
        obj.put("type", "expecting");
        obj.put("counter", this.counter);

        reply(obj.toJSONString());

    }

    // Request new block
    private void requestNewBlock()
    {
        
        // Construct a json message of newblock
        JSONObject obj = new JSONObject();
        obj.put("type", "exception");
        obj.put("counter", this.counter);

        reply(obj.toJSONString());
    }

    // Reply the status of handled instruction
    private void reply(String msg)
    {
        try{
            byte[] buf = msg.getBytes();
            DatagramPacket request = new DatagramPacket(buf, buf.length, host, port);
            socket.send(request);
            System.out.println("Sending: " + msg);
		} catch (SocketException e){
			e.printStackTrace();
		} catch (IOException e){
			e.printStackTrace();
		} 
    }

    private void processInstruction(String inst){

		InstructionFactory instFact=new InstructionFactory();
        Instruction receivedInst = instFact.FromJSON(inst);
			
        try {
            // The Server processes the instruction
            toFile.ProcessInstruction(receivedInst);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1); // just die at the first sign of trouble
        } catch (BlockUnavailableException e) {
            // The server does not have the bytes referred to by the block hash.
            try {
                /*
                 * At this point the Server needs to send a request back to the Client
                 * to obtain the actual bytes of the block.
                 */
                
                requestNewBlock(); 
                String msg = receive();
                System.out.println("Receive: " + msg);

                // Handle new block
                JSONObject obj=null;
                
                try {
                    JSONParser parser = new JSONParser();
                    obj = (JSONObject) parser.parse(msg);
                } catch (org.json.simple.parser.ParseException ex) {
                    ex.printStackTrace();
                    System.exit(-1);
                }
                if(obj!=null){
                    String type = (String) obj.get("type");
                    JSONObject instruction = (JSONObject) obj.get("inst");
                    //int counter = (int)(long)obj.get("counter");
                    int counter = ((Long)obj.get("counter")).intValue();
                // network delay
                
                /*
                 * Server receives the NewBlock instruction.
                 */
                    Instruction receivedInst2 = instFact.FromJSON(
                            instruction.toJSONString());
                    toFile.ProcessInstruction(receivedInst2);
                }
            } catch (IOException e1) {
                e1.printStackTrace();
                System.exit(-1);
            } catch (BlockUnavailableException e1) {
                assert(false); // a NewBlockInstruction can never throw this exception
            }
        }
        
    }

	@Override
	public void run() {

		//try {
			System.out.println("Server is running...");

			while (true) {
                String msg = receive();
                processMessage(msg);
		} // get next instruction loop forever
	}
}
