/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

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

@SuppressWarnings("serial")
public class TeensyMonitor extends AbstractTextMonitor {

  private Serial serial;
  private int serialRate;
  private Thread reopener;
  private Thread onlineChecker;
  private boolean isOpen;
  private final boolean debug = false;

  public TeensyMonitor(BoardPort port) {
    super(port);
    if (debug) System.out.println("TeensyMonitor constructor " + port.getAddress());

    serialRate = PreferencesData.getInteger("serial.debug_rate");
    serialRates.setSelectedItem(serialRate + " " + tr("baud"));
    isOpen = false;
    onSerialRateChange(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        String wholeString = (String) serialRates.getSelectedItem();
        String rateString = wholeString.substring(0, wholeString.indexOf(' '));
        serialRate = Integer.parseInt(rateString);
        PreferencesData.set("serial.debug_rate", rateString);
        try {
          close();
          Thread.sleep(100); // Wait for serial port to properly close
          open();
        } catch (InterruptedException e) {
          // noop
        } catch (Exception e) {
          System.err.println(e);
        }
      }
    });

    onSendCommand(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        send(textField.getText());
        textField.setText("");
      }
    });

    onClearCommand(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        textArea.setText("");
      }
    });
  }

  private void send(String s) {
    if (serial != null) {
      switch (lineEndings.getSelectedIndex()) {
        case 1:
          s += "\n";
          break;
        case 2:
          s += "\r";
          break;
        case 3:
          s += "\r\n";
          break;
      }
      if ("".equals(s) && lineEndings.getSelectedIndex() == 0 && !PreferencesData.has("runtime.line.ending.alert.notified")) {
        noLineEndingAlert.setForeground(Color.RED);
        PreferencesData.set("runtime.line.ending.alert.notified", "true");
      }
      serial.write(s);
    }
  }

  public void enableWindow(boolean enable) {
    if (debug) System.out.println("TeensyMonitor enableWindow");
    super.enableWindow(enable);
  }

  private String teensyPortName() {
    String addr = getBoardPort().getAddress();
    if (addr.equals("fake serial")) return "(emulated serial)";
    return addr;
//    if (BaseNoGui.getBoardPreferences().get("fake_serial") == null) {
//       return getBoardPort().getAddress();
//    } else {
//       return "(emulated serial)";
//    }
  }

  // called from Editor.java
  public void open() throws Exception {
    String port = teensyPortName();
    if (debug) System.out.println("TeensyMonitor open " + port);
    if (Thread.currentThread() != reopener) reopen_abort();
    if (serial != null) return;
    super.open();
    if (onlineChecker != null) onlineChecker.interrupt();

    PreferencesMap prefs = BaseNoGui.getBoardPreferences();
    //String fake = prefs.get("fake_serial");
    String fake = null;
    if (getBoardPort().getAddress().equals("fake serial")) fake = "teensy_gateway";
    
    boolean restart = prefs.getBoolean("serial.restart_cmd");

    if (fake == null) {
      // real USB serial
      if (port == null) throw new SerialException("No serial port, please select with Tools > Port");
      if (restart) {
        serial = new Serial(port, 150) {
          @Override
          protected void message(char buff[], int n) {
            addToUpdateBuffer(buff, n);
          }
        };
        serial.setBaud(serialRate);
      } else {
        serial = new Serial(port, serialRate) {
          @Override
          protected void message(char buff[], int n) {
            addToUpdateBuffer(buff, n);
          }
        };
      }
    } else {
      // fake serial emulated with HID & teensy_gateway
      if (restart) {
        String cmdline = BaseNoGui.getHardwarePath() + File.separator
        + "tools" + File.separator + "teensy_restart";
        try {
          Runtime.getRuntime().exec(new String[] {cmdline});
        } catch (Exception e) {
        }
      }
      serial = new FakeSerial(fake) {
        @Override
        protected void message(char buff[], int n) {
          addToUpdateBuffer(buff, n);
        }
      };
    }
    // we only get here if a connection was really opened
    textArea.setText("");
    isOpen = true;
    enableWindow(true);
    setTitle("TeensyMonitor: " + port + " Online");

    onlineChecker = new Thread() {
      public void run() {
        while (true) {
          try {
            sleep(400);
          } catch (InterruptedException e) {
            return;
          }
          if (serial == null) return;
          if (!(serial.isOnline())) {
            if (debug) System.out.println("Serial went offline");
            try {
              close();
              reopen();  // starts reopener thread
            } catch (Exception e) {
            }
            return;
          }
        }
      }
    };
    onlineChecker.start();
  }


  // called from Editor.java
  public void suspend() throws Exception {
    if (debug) System.out.println("TeensyMonitor suspend");
    super.suspend();
  }

  // called from Editor.java
  public void resume(BoardPort boardPort) throws Exception {
    // TODO: can boardPort ever try to resume a different port??
    if (debug) System.out.println("TeensyMonitor resume " + boardPort.getAddress());
    //super.resume(boardPort);

    if (!isVisible()) return;
    reopen_abort();
    setBoardPort(boardPort);
    setTitle("TeensyMonitor: " + teensyPortName() + " Offline");
    if (isOpen) return;

    reopener = new Thread() {
      public void run() {
        int initial_delay = 800;
        // trying too early on Windows risks triggering a horrible
        // Windows driver bug, so be extra careful on Windows
        if (OSUtils.isWindows()) initial_delay += 1000;
        try {
          sleep(initial_delay);
        } catch (InterruptedException e) {
          return;
        }
        int attempt = 0;
        while (true) {  // keep trying as long as the window is visible
          attempt++;
          //if (debug) System.out.println("reopener attempt # " + attempt);
          try {
            sleep((attempt < 50) ? 100 : 500);
          } catch (InterruptedException e) {
            return;
          }
          try {
            open();
            return;
          } catch (Exception e) {
            // open throws exception if unable to open
          }
	  //if (debug && attempt > 60) return;
        }
      }
    };
    reopener.start();
  }

  // called from the onlineChecker thread, to restart the reopener thread
  // after the port goes offline and needs to auto-reopen when back online
  private void reopen() throws Exception {
    resume(getBoardPort());
  }

  private void interrupt_thread(Thread th) {
    if (th != null && th != Thread.currentThread() && th.isAlive()) th.interrupt();
  }

  // called from Editor.java
  public void close() throws Exception {
    if (debug) System.out.println("TeensyMonitor close");
    setTitle("TeensyMonitor: Closed");
    interrupt_thread(reopener);
    interrupt_thread(onlineChecker);
    isOpen = false;
    super.close();
    if (serial != null) {
      int[] location = getPlacement();
      String locationStr = PApplet.join(PApplet.str(location), ",");
      PreferencesData.set("last.serial.location", locationStr);
      enableWindow(false);
      serial.dispose();
      serial = null;
    }
  }

  private void reopen_abort() {
    if (reopener == null) return;
    int attempt = 0;
    while (attempt < 25) {  // keep trying for approx 1/4 second
      if (!reopener.isAlive()) {
        reopener = null;
        break;
      }
      if (attempt == 0) reopener.interrupt();
      try {
        Thread.sleep(10);
        attempt++;
      } catch (InterruptedException e) {
      }
    }
  }

}


class FakeSerial extends Serial {
	Socket sock=null;
	InputStream input;
	OutputStream output;
	inputListener listener=null;
	int[] addrlist = {28541,4984,18924,16924,27183,31091};
	static Process gateway=null;
	static boolean gateway_shutdown_scheduled=false;

	public FakeSerial(String name) throws SerialException {
		super("fake serial"); // prevents normal serial from also opening!
		int attempt=1;
		do {
			if (gateway_connect(name)) {
				listener = new inputListener();
				listener.input = input;
				listener.consumer = this;
				listener.start();
				return;
			}
			if (attempt <= 2 && !gateway_start(name)) {
				System.err.println("Error starting " + name);
			}
			delay_20ms();
		} while (++attempt < 4);
		throw new SerialException("no connection");
	}
	private boolean gateway_connect(String name) {
		int namelen = name.length();
		byte[] buf = new byte[namelen];
		byte[] namebuf = name.getBytes();
		InetAddress local;
		try {
			byte[] loop = new byte[] {127, 0, 0, 1};
			local = InetAddress.getByAddress("localhost", loop);
		} catch (Exception e) {
			sock = null;
			return false;
		}
		for (int i=0; i<addrlist.length; i++) {
			try {
				sock = new Socket();
				InetSocketAddress addr = new InetSocketAddress(local, addrlist[i]);
				sock.connect(addr, 50); // if none, should timeout instantly
							// but windows will wait up to 1 sec!
				input = sock.getInputStream();
				output = sock.getOutputStream();
			} catch (Exception e) {
				sock = null;
				return false;
			}
			// check for welcome message
			try {
				int wait = 0;
				while (input.available() < namelen) {
					if (++wait > 6) throw new Exception();
					delay_20ms();
				}
				input.read(buf, 0, namelen);
				String id = new String(buf, 0, namelen);
				for (int n=0; n<namelen; n++) {
					if (buf[n] !=  namebuf[n]) throw new Exception();
				}
			} catch (Exception e) {
				// mistakenly connected to some other program!
				close_sock();
				continue;
			}
                        //System.out.println("gateway_connect ok");
			return true;
		}
		sock = null;
		return false;
	}
	private void close_sock() {
		try {
			sock.close();
		} catch (Exception e) { }
		sock = null;
	}
	private void delay_20ms() {
		try {
			Thread.sleep(20);
		} catch (Exception e) { }
	}
	@Override
	public void dispose() {
		if (listener != null) {
			listener.interrupt();
			listener.consumer = null;
			listener = null;
		}
		if (sock != null) {
			try {
				sock.close();
			} catch (Exception e) { }
			sock = null;
		}
	       dispose_gateway();
	}
	public static void dispose_gateway() {
		if (gateway != null) {
			gateway.destroy();
			gateway = null;
		}
	}
	private boolean gateway_start(String cmd) {
		String cmdline = BaseNoGui.getHardwarePath() + File.separator
			+ "tools" + File.separator + cmd;
		try {
			dispose_gateway();
			gateway = Runtime.getRuntime().exec(new String[] {cmdline});
			if (!gateway_shutdown_scheduled) {
				Runtime.getRuntime().addShutdownHook(new Thread() {
					public void run() {
						FakeSerial.dispose_gateway();
					}
				});
				gateway_shutdown_scheduled = true;
			}
		} catch (Exception e) {
			dispose_gateway();
			return false;
		}
		return true;
	}
	protected void message(char[] chars, int length) {
		// override from SerialMonitor
	}
	@Override
	public void write(byte bytes[]) {
		if (output == null) return;
		if (bytes.length > 0) {
			try {
				output.write(bytes, 0, bytes.length);
			} catch (IOException e) { }
		}
	}
	@Override
	public void write(int what) {
		byte[] b = new byte[1];
		b[0] = (byte)(what & 0xff);
		write(b);
	}
	@Override
	public void setDTR(boolean state) {
	}
	@Override
	public void setRTS(boolean state) {
	}
	@Override
	public boolean isOnline() {
		return true;
	}
	static public ArrayList<String> list() {
		return new ArrayList<String>();
	}
}

class inputListener extends Thread {
	FakeSerial consumer = null;
	InputStream input;

	public void run() {
		byte[] buffer = new byte[1024];
		int num, errcount=0;
		try {
			while (true) {
				num = input.read(buffer);
				if (num <= 0) break;
				//System.out.println("inputListener, n = " + num);
				if (consumer != null) {
					String msg = new String(buffer, 0, num);
					char[] chars = msg.toCharArray();
					consumer.message(chars, chars.length);
				}
			}
		} catch (Exception e) { }
	}
}
