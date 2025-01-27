package com.example.messagingstompwebsocket;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GreetingIntegrationTests {

	@LocalServerPort
	private int port;

	private WebSocketStompClient stompClient;

	private final WebSocketHttpHeaders headers = new WebSocketHttpHeaders();

	@BeforeEach
	public void setup() {
		final List<Transport> transports = new CopyOnWriteArrayList<>();
		final WebSocketClient webSocketClient = new StandardWebSocketClient();
		final Transport webSocketTransport = new WebSocketTransport(webSocketClient);
		transports.add(webSocketTransport);
		final WebSocketClient sockJsClient = new SockJsClient(transports);

		this.stompClient = new WebSocketStompClient(sockJsClient);
		final SmartMessageConverter messageConverter = new MappingJackson2MessageConverter();
		this.stompClient.setMessageConverter(messageConverter);
	}

	@Test
	void getGreeting() throws Exception {

		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicReference<Throwable> failure = new AtomicReference<>();

		// StompSessionHandler type is declared since method .connect() is used
		final StompSessionHandler handler = new TestSessionHandler(failure) {

			@Override
			public void afterConnected(final @NotNull StompSession session, @NotNull StompHeaders connectedHeaders) {
				session.subscribe("/topic/greetings", new StompFrameHandler() {
					@Override
					public @NotNull Type getPayloadType(@NotNull StompHeaders headers) {
						return Greeting.class;
					}

					@Override
					public void handleFrame(@NotNull StompHeaders headers, Object payload) {
						final Greeting<String> greeting = (Greeting<String>) payload;
						try {
							assertEquals("Hello, Spring!", greeting.getContent());
						} catch (Throwable t) {
							failure.set(t);
						} finally {
							session.disconnect();
							latch.countDown();
						}
					}
				});
				try {
					session.send("/app/hello", new HelloMessage("Spring"));
				} catch (Throwable t) {
					failure.set(t);
					latch.countDown();
				}
			}
		};

		this.stompClient.connect("ws://localhost:{port}/gs-guide-websocket", this.headers, handler, this.port);

		if (latch.await(3, TimeUnit.SECONDS)) {
			if (failure.get() != null) {
				throw new AssertionError("", failure.get());
			}
		}
		else {
			fail("Greeting not received");
		}

	}

	private static class TestSessionHandler extends StompSessionHandlerAdapter {

		private final AtomicReference<Throwable> failure;

		public TestSessionHandler(AtomicReference<Throwable> failure) {
			this.failure = failure;
		}

		@Override
		public void handleFrame(@NotNull StompHeaders headers, Object payload) {
			this.failure.set(new Exception(headers.toString()));
		}

		@Override
		public void handleException(@NotNull StompSession s, StompCommand c, @NotNull StompHeaders h, byte @NotNull [] p, @NotNull Throwable ex) {
			this.failure.set(ex);
		}

		@Override
		public void handleTransportError(@NotNull StompSession session, @NotNull Throwable ex) {
			this.failure.set(ex);
		}
	}
}
