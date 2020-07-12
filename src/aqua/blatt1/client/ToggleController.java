package aqua.blatt1.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.rmi.RemoteException;

public class ToggleController implements ActionListener {
	private final String fishID;
	private final TankModel tankModel;

	public ToggleController(String fishID, TankModel tankModel) {
		this.fishID = fishID;
		this.tankModel = tankModel;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
			tankModel.locateFishGlobally(fishID);
		} catch (RemoteException remoteException) {
			remoteException.printStackTrace();
		}
	}
}
