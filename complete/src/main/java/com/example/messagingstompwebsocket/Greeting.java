package com.example.messagingstompwebsocket;

public class Greeting<E> {

	private E content;

	public Greeting() {
	}

	public Greeting(E content) {
		this.content = content;
	}

	public E getContent() {
		return content;
	}

}
