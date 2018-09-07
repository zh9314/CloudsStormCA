

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
	private String prompt = null;
	
	private String endTag = "2c00aa94-d5fe-4ebe-a8fb-d0a5a007ce1f"+"75cd2504-8f67-4ffe-a039-45508aa8c10c";
	private boolean endStdOutput = false, endErrOutput = false;
	
	@OnOpen
	public void onOpen(Session session, @PathParam("ctrlIP") String ctrlIP){
		System.out.println("Success");
		curSession = session;
		startBash(ctrlIP);
	}
	
	@OnMessage
    public void onMessage(Session session, byte[] message, @PathParam("ctrlIP") String ctrlIP) {
		System.out.println("GET A BMSG! "+message.length+" "+ message[0]+" "+ message[1]);
		
		
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
		System.out.println("GET A MSG! "+message);
		try {
			if(message.trim().equals("clear")){
				curSession.getBasicRemote().sendText("\033[2J\033[0;0H"+prompt);
				return;
			} 
			
			if(message.trim().equals("exit")){
				curSession.getBasicRemote().sendText("\r\n"+prompt);
				curSession.close();
				return;
			}
		
			Writer strWriter = new OutputStreamWriter(curShell.getOutputStream());
		
			endStdOutput = false;
			endErrOutput = false;
			strWriter.write(message+"\n");
			strWriter.flush();
			
			strWriter.write("echo '"+endTag+"'; "+endTag+"\n");
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
	
	
	private void startBash(String ctrlIP){
		ProcessBuilder pb = new ProcessBuilder("/bin/bash");
		try {
			pb.redirectErrorStream(true);
			curShell = pb.start();
			stdInput = new BufferedReader(new 
				     InputStreamReader(curShell.getInputStream()));
			prompt = "\033[1;3;31mroot@ctrl\033[1;3;33m("+ctrlIP+")\033[0m # ";
			String promptPure = "root@ctrl("+ctrlIP+") # ";
			curSession.getBasicRemote().sendText("prompt::::"+prompt);
			curSession.getBasicRemote().sendText("promptPure::::"+promptPure);
			welcomePage();
			curSession.getBasicRemote().sendText("\r\n"+prompt);
			new Thread(new Runnable(){
			    public void run(){
			    		try {
			    			String returnString = "";
			    	    		while ((returnString = stdInput.readLine()) != null) {
			    	    			System.out.println(returnString);
			    	    			if(returnString.equals(endTag))
			    	    				endStdOutput = true;
			    	    			else if(returnString.contains(endTag) && returnString.contains("not found"))
		    	    					endErrOutput = true;
			    	    			else if(returnString.equals("echo '"+endTag+"'; "+endTag) )
			    	    				;
			    	    			else
			    	    				curSession.getBasicRemote().sendText(returnString+"\r\n");
		    	    				
		    	    				if(endStdOutput && endErrOutput)
		    	    					curSession.getBasicRemote().sendText(prompt);
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
	
	

}