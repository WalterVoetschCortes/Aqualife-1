package aqua.blatt1.client;

import javax.crypto.NoSuchPaddingException;
import javax.swing.SwingUtilities;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class Aqualife {

	public static void main(String[] args) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
		ClientCommunicator communicator = new ClientCommunicator();
		TankModel tankModel11 = new TankModel(communicator.newClientForwarder());

		communicator.newClientReceiver(tankModel11).start();

		SwingUtilities.invokeLater(new AquaGui(tankModel11));

		tankModel11.run();
	}

}
