package aqua.blatt1.client;

import javax.swing.SwingUtilities;

public class Aqualife {

	public static void main(String[] args) {
		ClientCommunicator communicator = new ClientCommunicator();
		TankModel tankModel11 = new TankModel(communicator.newClientForwarder());

		communicator.newClientReceiver(tankModel11).start();

		SwingUtilities.invokeLater(new AquaGui(tankModel11));

		tankModel11.run();
	}

}
