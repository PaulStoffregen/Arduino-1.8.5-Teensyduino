/*
 * This file is part of Arduino.
 *
 * Arduino is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * As a special exception, you may use this file as part of a free software
 * library without restriction.  Specifically, if other files instantiate
 * templates or use macros or inline functions from this file, or you compile
 * this file and link it with other files to produce an executable, this
 * file does not by itself cause the resulting executable to be covered by
 * the GNU General Public License.  This exception does not however
 * invalidate any other reasons why the executable file might be covered by
 * the GNU General Public License.
 *
 * Copyright 2013 Arduino LLC (http://www.arduino.cc/)
 */

package cc.arduino.packages.discoverers;

import cc.arduino.packages.BoardPort;
import cc.arduino.packages.Discovery;
import cc.arduino.packages.discoverers.serial.SerialBoardsLister;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;

public class SerialDiscovery implements Discovery {

  private Timer serialBoardsListerTimer;
  private final List<BoardPort> serialBoardPorts;
  private SerialBoardsLister serialBoardsLister = new SerialBoardsLister(this);

  public SerialDiscovery() {
    this.serialBoardPorts = new LinkedList<>();
  }

  @Override
  public List<BoardPort> listDiscoveredBoards() {
    return getSerialBoardPorts(false);
  }

  @Override
  public List<BoardPort> listDiscoveredBoards(boolean complete) {
    return getSerialBoardPorts(complete);
  }

  private List<BoardPort> getSerialBoardPorts(boolean complete) {
      if (complete) {
        return new LinkedList<>(serialBoardPorts);
      }
      List<BoardPort> onlineBoardPorts = new LinkedList<>();
      for (BoardPort port : serialBoardPorts) {
        if (port.isOnline() == true) {
          onlineBoardPorts.add(port);
        }
      }
      return onlineBoardPorts;
  }

  public void setSerialBoardPorts(List<BoardPort> newSerialBoardPorts) {
      serialBoardPorts.clear();
      serialBoardPorts.addAll(newSerialBoardPorts);
  }

  public void forceRefresh() {
    serialBoardsLister.retriggerDiscovery(false);
  }

  public void setUploadInProgress(boolean param) {
    serialBoardsLister.uploadInProgress = param;
  }

  public void pausePolling(boolean param) { serialBoardsLister.pausePolling = param;}

  @Override
  public void start() {
    this.serialBoardsListerTimer = new Timer(SerialBoardsLister.class.getName());
    serialBoardsLister.start(serialBoardsListerTimer);
  }

  @Override
  public void stop() {
    this.serialBoardsListerTimer.purge();
  }
}
