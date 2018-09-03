

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;


@ServerEndpoint(value = "/csterm/{ctrlIP}")
public class RMTerm {
	private Session curSession = null;
	private Process curShell = null;
	private BufferedReader stdInput = null;
	private boolean thereExistOutput = false;
	private String prompt = null;
	private boolean enterBlocked = false;   /// block the enter key
	
	@OnOpen
	public void onOpen(Session session, @PathParam("ctrlIP") String ctrlIP){
		System.out.println("Success");
		curSession = session;
		startBash(ctrlIP);
	}
	
	@OnMessage
    public void onMessage(Session session, byte[] message, @PathParam("ctrlIP") String ctrlIP) {
		System.out.println("GET A BMSG! "+message.length+" "+ message[0]+" "+ message[1]);
		if(message[0] == 13){
			ReadChecker rc = new ReadChecker();
			Thread check = new Thread(rc);
			check.start();
			try {
				Thread.sleep(400);
				if(!enterBlocked && !thereExistOutput){
					Thread.sleep(100);
					if(!rc.sth && curShell != null)
						session.getBasicRemote().sendText("\r\n"+prompt);
				}
				enterBlocked = false;
			} catch ( IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		if(message[1] == 3 || message[1] == 4){
			if(curShell != null){
				curShell.destroyForcibly();
				curShell = null;
			}
			curSession = session;
			try {
				curSession.getBasicRemote().sendText("\033[2J\033[0;0H");
			} catch (IOException e) {
				e.printStackTrace();
			}
			startBash(ctrlIP);
		}
	}
	
	@OnMessage
    public void onMessage(Session session, String message) {
		thereExistOutput = false;
		if(message.trim().equals("")){
			enterBlocked = true;
			return ;
		}
		System.out.println("GET A MSG! "+message);
		try {
			if(message.trim().equals("clear")){
				curSession.getBasicRemote().sendText("\033[2J\033[0;0H"+prompt);
				enterBlocked = true;
				return;
			} 
			
			if(message.trim().equals("exit")){
				curSession.getBasicRemote().sendText("\r\n"+prompt);
				curSession.close();
				return;
			}
		
			Writer strWriter = new OutputStreamWriter(curShell.getOutputStream());
		
			strWriter.write(message+"\n");
			strWriter.flush();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@OnClose
	public void onClose() {
		if(curShell != null){
			curShell.destroyForcibly();
			curShell = null;
		}
	}
	
	/*@OnError
	public void onError(Throwable t) throws Throwable {
		System.out.println("Error");
		if(curShell != null){
			curShell.destroyForcibly();
			curShell = null;
		}	
	    // Most likely cause is a user closing their browser. Check to see if
	    // the root cause is EOF and if it is ignore it.
	    // Protect against infinite loops.
	    int count = 0;
	    Throwable root = t;
	    while (root.getCause() != null && count < 20) {
	        root = root.getCause();
	        count ++;
	    }
	    if (root instanceof EOFException) {
	        // Assume this is triggered by the user closing their browser and
	        // ignore it.
	    } else {
	        throw t;
	    }
	}*/
	
	private void startBash(String ctrlIP){
		ProcessBuilder pb = new ProcessBuilder("/bin/bash");
		try {
			pb.redirectErrorStream(true);
			curShell = pb.start();
			stdInput = new BufferedReader(new 
				     InputStreamReader(curShell.getInputStream()));
			prompt = "\033[1;3;31mroot@ctrl\033[1;3;33m("+ctrlIP+")\033[0m # ";
			curSession.getBasicRemote().sendText("prompt::::"+prompt);
			welcomePage();
			curSession.getBasicRemote().sendText("\r\n"+prompt);
			thereExistOutput = false;
			new Thread(new Runnable(){
			    public void run(){
			    		try {
			    			String returnString = "";
			    	    		while ((returnString = stdInput.readLine()) != null) {
			    	    			System.out.println(returnString);
		    	    				curSession.getBasicRemote().sendText("\r\n"+returnString);
			    	    			thereExistOutput = true;
			    	    			if(!stdInput.ready())
			    	    				curSession.getBasicRemote().sendText("\r\n"+prompt);
			    	    		}
			    	    		System.out.println("Terminated");
			    	    		
					} catch (IOException e) {
						e.printStackTrace();
					}
			    }
			}).start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void welcomePage(){
		try {
			String CloudsStormText = "  _______             __    ______               \r\n";
			      CloudsStormText += " / ___/ /__  __ _____/ /__ / __/ /____  ______ _ \r\n";
			      CloudsStormText += "/ /__/ / _ \\/ // / _  (_-<_\\ \\/ __/ _ \\/ __/  ' \\\r\n";
			      CloudsStormText += "\\___/_/\\___/\\_,_/\\_,_/___/___/\\__/\\___/_/ /_/_/_/\r\n";
			curSession.getBasicRemote().sendText("Welcome to the Control Agent of\r\n");
			curSession.getBasicRemote().sendText(CloudsStormText);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	class ReadChecker implements Runnable {
		public boolean sth = false;
		
		@Override
	    public void run(){
			try {
				int count = 10, i = 0;
				while(i<count){
	    	    			if(stdInput.ready()){
	    	    				sth = true;
	    	    			}
	    	    			Thread.sleep(100);
	    	    			i++;
				}
	    		
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
	    }
		
		
	}
	

}