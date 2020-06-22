package net.jibini.mycelium.spore;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.jibini.mycelium.api.Interaction;
import net.jibini.mycelium.api.InternalRequest;
import net.jibini.mycelium.api.Request;
import net.jibini.mycelium.api.RequestEvent;
import net.jibini.mycelium.event.Handles;
import net.jibini.mycelium.hook.Hook;
import net.jibini.mycelium.link.StitchPatch;
import net.jibini.mycelium.network.NetworkServer;

public class TestNetworkServerUplink
{
	private static Request received = null;
	
	private ServerSocket socket;
	
	public static class TestInteraction implements Interaction
	{
		@Override
		public Interaction spawn()
		{ return new TestInteraction(); }
		
		@Handles("TestRequest")
		public void testRequest(RequestEvent event)
		{ received = event.request(); }
	}
	
	@Before
	public void startServer() throws IOException
	{
		socket = new ServerSocket();
		socket.bind(new InetSocketAddress("0.0.0.0", 25605));
		
		new Thread(() ->
		{
			try
			{
				StitchPatch patch = new StitchPatch()
						.withName("Endpoint");
				
				NetworkServer server = new NetworkServer();
				server
						.attach(patch)
						.embedInteraction()
						.withServerSocket(socket)
						.start();
				patch.send(patch.read());
				
				Thread.sleep(700);
				patch.close();
				server.close();
			} catch (Exception ex)
			{  }
		}).start();
	}
	
	private static final SporeProfile testProfile = new SporeProfile()
			{
				@Override
				public String serviceName()
				{ return "TestSpore"; }

				@Override
				public String version()
				{ return "1.0"; }

//				@Override
//				public int protocolVersion()
//				{ return 1; }
			};
			
	public static class TestSpore extends AbstractSpore
	{
		@Override
		public SporeProfile profile()
		{ return testProfile; }
		

		@Hook(Spore.HOOK_UPLINK)
		public void postUplink()
		{
			interactions().registerStartPoint("TestRequest", new TestInteraction());
			
			uplink().send(new InternalRequest()
//					.withHeader("interaction", UUID.randomUUID())
					.withTarget("Endpoint")
					.withRequest("TestRequest"));
		}

		@Hook(Spore.HOOK_SERVICE_AVAILABLE)
		public void postServiceAvailable()
		{  }
	}
	
	@Test(timeout=2000)
	public void testUplinkEcho() throws InterruptedException
	{
		new TestSpore().start();
			
		assertEquals("Endpoint", received.header().get("target"));
	}
	
	@After
	public void closeServer()
	{
		try
		{
			socket.close();
		} catch (IOException ex)
		{  }
	}
}
