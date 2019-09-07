/**
 *  MicroEmulator
 *  Copyright (C) 2006-2007 Bartek Teodorczyk <barteo@barteo.net>
 *  Copyright (C) 2006-2007 Vlad Skarzhevskyy
 *
 *  It is licensed under the following two licenses as alternatives:
 *    1. GNU Lesser General Public License (the "LGPL") version 2.1 or any newer version
 *    2. Apache License (the "AL") Version 2.0
 *
 *  You may not use this file except in compliance with at least one of
 *  the above two licenses.
 *
 *  You may obtain a copy of the LGPL at
 *      http://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt
 *
 *  You may obtain a copy of the AL at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the LGPL or the AL for the specific language governing permissions and
 *  limitations.
 *
 *  @version $Id: FileSystemConnectorImpl.java 1605 2008-02-25 21:07:14Z barteo $
 */
package org.microemu.cldc.file;

import java.io.IOException;
import java.util.List;
import java.util.Vector;

import javax.microedition.io.Connection;

import org.microemu.log.Logger;
import org.microemu.microedition.ImplementationUnloadable;
import org.microemu.microedition.io.ConnectorAdapter;

/**
 * @author vlads
 * 
 */
public class FileSystemConnectorImpl extends ConnectorAdapter implements ImplementationUnloadable {

	public final static String PROTOCOL = org.microemu.cldc.file.Connection.PROTOCOL;

	private String fsRoot;

	private List openConnection = new Vector();

	FileSystemConnectorImpl(String fsRoot) {
		this.fsRoot = fsRoot;
	}

	public Connection open(final String name, int mode, boolean timeouts) throws IOException {
		// file://<host>/<path>
		if (!name.startsWith(PROTOCOL)) {
			throw new IOException("Invalid Protocol " + name);
		}
		Connection con = new FileSystemFileConnection(fsRoot, name.substring(PROTOCOL.length()),
						FileSystemConnectorImpl.this);
		openConnection.add(con);
		return con;
	}

  public java.io.InputStream openInputStream(String name) throws IOException {
		// file://<host>/<path>
		if (!name.startsWith(PROTOCOL)) {
			throw new IOException("Invalid Protocol " + name);
		}
    return new java.io.FileInputStream(name.substring(PROTOCOL.length()));
  }

	void notifyMIDletDestroyed() {
		if (openConnection.size() > 0) {
			Logger.warn("Still has " + openConnection.size() + " open file connections:");
			for (int i = 0; i < openConnection.size(); i++) {
				Logger.info("\t" + ((javax.microedition.io.file.FileConnection) openConnection.get(i)).getURL());
      }
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.microemu.microedition.ImplementationUnloadable#unregisterImplementation()
	 */
	public void unregisterImplementation() {
		FileSystem.unregisterImplementation(this);
	}

	void notifyClosed(FileSystemFileConnection con) {
		openConnection.remove(con);
	}

}
