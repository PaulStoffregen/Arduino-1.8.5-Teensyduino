package cc.arduino.packages.discoverers;

import cc.arduino.packages.BoardPort;
import cc.arduino.packages.Discovery;
import processing.app.BaseNoGui;

import java.util.LinkedList;
import java.util.List;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;


public class TeensyDiscovery implements Discovery, Runnable {
	private final List<BoardPort> portlist;
	private final Thread thread;
	private Process program=null;
	private JsonParser parser;
	private ObjectMapper mapper;

	public TeensyDiscovery() {
		portlist = new LinkedList<>();
		thread = new Thread(this);
	}

	public void run() {
		String cmdline = BaseNoGui.getHardwarePath() + File.separator
			+ "tools" + File.separator + "teensy_ports";
		try {
			program = Runtime.getRuntime().exec(new String[] {cmdline, "-J"});
		} catch (Exception e1) {
			program = null;
		}
		if (program == null) {
			print("unable to run teensy_ports -J");
			return;
		}
		InputStream input = program.getInputStream();
		print("teensy_ports -J started");
		try {
			// https://sohlich.github.io/post/jackson/
			// https://www.baeldung.com/jackson-object-mapper-tutorial
			JsonFactory factory = new JsonFactory();
			parser = factory.createParser(input);
			mapper = new ObjectMapper();
			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			while (true) {
				BoardPort port = mapper.readValue(parser, BoardPort.class);
				if (port != null) {
					incoming(port);
				}
			}
		} catch (Exception e) {
			print("ended with exception");
		}
		print("end");
	}

	private synchronized void incoming(BoardPort port) {
		String address = port.getAddress();
		if (address == null) {
			return; // address is required
		}
		for (BoardPort bp : portlist) {
			if (address.equals(bp.getAddress())) {
				// if address already on the list, discard old info
				portlist.remove(bp);
			}
		}
		if (port.isOnline()) {
			if (port.getLabel() == null) {
				// if no label, use address
				port.setLabel(address);
			}
			if (port.getProtocol() == null) {
				// if no protocol, assume serial
				port.setProtocol("serial");
			}
			portlist.add(new BoardPort(port));
		}
	}

	@Override
	public void start() {
		print("start");
		try {
			thread.start();
		} catch (Exception e) {
		}
	}

	@Override
	public void stop() {
		print("stop");
		thread.interrupt();
		if (program != null) {
			program.destroy();
			program = null;
		}
	}

	@Override
	public List<BoardPort> listDiscoveredBoards() {
		return listDiscoveredBoards(false);
	}

	@Override
	public synchronized List<BoardPort> listDiscoveredBoards(boolean complete) {
		print("listDiscoveredBoards");
		List<BoardPort> portlistCopy = new LinkedList<>();
		for (BoardPort bp : portlist) {
			portlistCopy.add(new BoardPort(bp));
		}
		return portlistCopy;
	}

	private void print(String str) {
		//System.err.println("TeensyDiscovery: " + str);
	}
}

