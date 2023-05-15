package com.example.datarecorderapp.postmeasurment;

public class Placement {

	private final long id;
	private final String content;

	public Placement(long id, String content) {
		this.id = id;
		this.content = content;
	}

	public long getId() {
		return id;
	}

	public String getContent() {
		return content;
	}
}
