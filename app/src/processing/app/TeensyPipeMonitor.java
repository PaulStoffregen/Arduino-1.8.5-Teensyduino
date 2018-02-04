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

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.util.*;
import processing.app.helpers.PreferencesMap;
import processing.app.helpers.OSUtils;
import static processing.app.I18n.tr;

public class TeensyPipeMonitor extends AbstractTextMonitor {

	private final boolean debug = true;
	Process program=null;
	inputPipeListener listener=null;

	public TeensyPipeMonitor(BoardPort port) {
		super(port);
		if (debug) System.out.println("TeensyPipeMonitor ctor, port=" + port.getAddress());

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
							System.out.println("wrote " + b.length);
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

		String[] cmdline = new String[2];
		cmdline[0] = BaseNoGui.getHardwarePath() + File.separator +
			"tools" + File.separator + "teensy_serialmon";
		cmdline[1] = port;
		try {
			program = Runtime.getRuntime().exec(cmdline);
		} catch (Exception e1) {
			System.err.println("Unable to run teensy_serialmon");
			program = null;
		}
		if (program != null) {
			textArea.setText("");
			listener = new inputPipeListener();
			listener.input = program.getInputStream();
			listener.output = this;
			listener.start();
			super.open();
		}
	}

	public void close() throws Exception {
		if (debug) System.out.println("TeensyPipeMonitor close");
		program.destroy();
		program = null;
		super.close();
	}

};

class inputPipeListener extends Thread {
	InputStream input;
	TeensyPipeMonitor output;

	public void run() {
		byte[] buffer = new byte[1024];
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
	}

}

