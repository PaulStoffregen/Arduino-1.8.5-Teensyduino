/*
  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app;

import cc.arduino.packages.BoardPort;
import processing.app.legacy.PApplet;
import processing.app.PreferencesData;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.*;
import static processing.app.I18n.tr;

public class TeensyPipeMonitor extends AbstractTextMonitor {

	private final boolean debug = false;
	private String teensyname=null;
	private String openport=null;
	Process program=null;
	inputPipeListener listener=null;
	errorPipeListener errors=null;

	public TeensyPipeMonitor(BoardPort port) {
		super(port);
		if (debug) System.out.println("TeensyPipeMonitor ctor, port=" + port.getAddress());
		String[] pieces = port.getLabel().trim().split("[\\(\\)]");
		if (pieces.length > 2 && pieces[1].startsWith("Teensy")) {
			teensyname = pieces[1];
		} else {
			teensyname = "Teensy";
		}
		serialRates.hide();
		disconnect();

		onClearCommand(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				textArea.setText("");
			}
		});
		onSendCommand(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String s = textField.getText();
				switch (lineEndings.getSelectedIndex()) {
				  case 1: s += "\n"; break;
				  case 2: s += "\r"; break;
				  case 3: s += "\r\n"; break;
				}
				byte[] b = s.getBytes(); // TODO: is this UTF8?
				if (program != null) {
					OutputStream out = program.getOutputStream();
					if (out != null) {
						try {
							out.write(b);
							out.flush();
							//System.out.println("wrote " + b.length);
						} catch (Exception e1) { }
					}
				}
				textField.setText("");
			}
		});
	}

	public void open() throws Exception {
		String port = getBoardPort().getAddress();
		if (debug) System.out.println("TeensyPipeMonitor open " + port);
		if (openport != null && port.equals(openport) && program != null
		   && listener != null && listener.isAlive()
		   && errors != null && errors.isAlive()) {
			// correct port is already open
			if (debug) System.out.println("TeensyPipeMonitor port already open");
			return;
		}
		if (program != null || listener != null || errors != null) {
			if (debug) System.out.println("TeensyPipeMonitor close old before open");
			close();
		}
		String[] cmdline;
		String command = BaseNoGui.getHardwarePath() + File.separator +
			"tools" + File.separator + "teensy_serialmon";
		//if (PreferencesData.getBoolean("upload.verbose")) {
			//cmdline = new String[3];
			//cmdline[0] = command;
			//cmdline[1] = "-v";
			//cmdline[2] = port;
		//} else {
			cmdline = new String[2];
			cmdline[0] = command;
			cmdline[1] = port;
		//}
		try {
			program = Runtime.getRuntime().exec(cmdline);
		} catch (Exception e1) {
			System.err.println("Unable to run teensy_serialmon");
			program = null;
		}
		if (program != null) {
			openport = new String(port);
			textArea.setText("");
			listener = new inputPipeListener();
			listener.input = program.getInputStream();
			listener.output = this;
			listener.start();
			errors = new errorPipeListener();
			errors.input = program.getErrorStream();
			errors.output = this;
			errors.start();
			super.open();
		}
	}

	public void close() throws Exception {
		if (debug) System.out.println("TeensyPipeMonitor close");
		if (program != null) {
			program.destroy();
			program = null;
		}
		if (listener != null) {
			if (listener.isAlive()) listener.interrupt();
			listener = null;
		}
		if (errors != null) {
			if (errors.isAlive()) errors.interrupt();
			errors = null;
		}
		openport = null;
		setTitle("[offline] (" + teensyname + ")");
		super.close();
	}

	public void opened(String device, String usbtype) {
		if (debug) System.out.println("opened, dev=" + device + ", name=" + usbtype);
		textArea.setText("");
		setTitle(device + " (" + teensyname + ") " + usbtype);
		enableWindow(true);
	}

	public void disconnect() {
		if (debug) System.out.println("disconnect");
		setTitle("[offline] (" + teensyname + ")");
		enableWindow(false);
	}
};

class inputPipeListener extends Thread {
	InputStream input;
	TeensyPipeMonitor output;

	public void run() {
		byte[] buffer = new byte[65536];
		try {
			while (true) {
				int num = input.read(buffer);
				if (num <= 0) break;
				//System.out.println("inputPipeListener, num=" + num);
				String text = new String(buffer, 0, num);
				//System.out.println("inputPipeListener, text=" + text);
				char[] chars = text.toCharArray();
				output.addToUpdateBuffer(chars, chars.length);
				//System.out.println("inputPipeListener, out=" + chars.length);
			}
		} catch (Exception e) { }
		// System.out.println("inputPipeListener thread exit");
	}

}

class errorPipeListener extends Thread {
	InputStream input;
	TeensyPipeMonitor output;

	public void run() {
		InputStreamReader reader = new InputStreamReader(input);
		BufferedReader in = new BufferedReader(reader);
		try {
			while (true) {
				String line = in.readLine();
				//System.err.print("line: ");
				if (line.startsWith("Opened ")) {
					String parts[] = line.trim().split(" ", 3);
					if (parts.length == 3) {
						output.opened(parts[1], parts[2]);
					}
				} else if (line.startsWith("Disconnect ")) {
					output.disconnect();
				} else {
					System.err.println(line);
				}
			}
		} catch (Exception e) { }
		// System.out.println("errorPipeListener thread exit");
	}

}

