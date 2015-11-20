package com.huawei.insa2.comm.cmpp30;

import com.huawei.insa2.comm.*;
import com.huawei.insa2.comm.cmpp.CMPPConstant;
import com.huawei.insa2.comm.cmpp.CMPPWriter;
import com.huawei.insa2.comm.cmpp.message.*;
import com.huawei.insa2.comm.cmpp30.message.CMPP30ConnectRepMessage;
import com.huawei.insa2.util.Args;
import com.huawei.insa2.util.Resource;
import java.io.*;
import java.util.Date;

public class CMPP30Connection extends PSocketConnection {

	private int degree;
	private int hbnoResponseOut;
	private String source_addr;
	private int version;
	private String shared_secret;

	public CMPP30Connection(Args args) {
		degree = 0;
		hbnoResponseOut = 3;
		source_addr = null;

		hbnoResponseOut = args.get("heartbeat-noresponseout", 3);
		source_addr = args.get("source-addr", "huawei");
		version = args.get("version", 1);
		shared_secret = args.get("shared-secret", "");
		CMPPConstant.debug = args.get("debug", false);
		CMPPConstant.initConstant(getResource());
		init(args);
	}

	protected PWriter getWriter(OutputStream out) {
		return new CMPPWriter(out);
	}

	protected PReader getReader(InputStream in) {
		return new CMPP30Reader(in);
	}

	public int getChildId(PMessage message) {
		CMPPMessage mes = (CMPPMessage) message;
		int sequenceId = mes.getSequenceId();
		if (mes.getCommandId() == 5 || mes.getCommandId() == 8 || mes.getCommandId() == 2)
			return -1;
		else
			return sequenceId;
	}

	public PLayer createChild() {
		return new CMPP30Transaction(this);
	}

	public int getTransactionTimeout() {
		return super.transactionTimeout;
	}

	public Resource getResource() {
		try {
			Resource resource = new Resource(getClass(), "resource");
			return resource;
		} catch (IOException e) {
			e.printStackTrace();
		}
		Resource resource1 = null;
		return resource1;
	}

	public synchronized void waitAvailable() {
		try {
			if (getError() == PSocketConnection.NOT_INIT)
				wait(super.transactionTimeout);
		} catch (InterruptedException interruptedexception) {}
	}

	public void close() {
		CMPP30Transaction t = (CMPP30Transaction) createChild();
		CMPPTerminateMessage msg = new CMPPTerminateMessage();
		try {
			t.send(msg);
			t.waitResponse();
			CMPPTerminateRepMessage rsp = (CMPPTerminateRepMessage) t.getResponse();
			if (null == rsp)
				return;
		} catch (PException ex) {
			ex.printStackTrace();
		} finally {
			t.close();
		}
		
		super.close();
	}

	protected void heartbeat() {
		CMPP30Transaction t = (CMPP30Transaction) createChild();
		CMPPActiveMessage hbmes = new CMPPActiveMessage();
		try {
			t.send(hbmes);
			t.waitResponse();
			CMPPActiveRepMessage rsp = (CMPPActiveRepMessage) t.getResponse();
			if (rsp == null) {
				degree++;
				if (degree == hbnoResponseOut) {
					degree = 0;
					setError(CMPPConstant.HEARTBEAT_ABNORMITY);
				}
			} else {
				degree = 0;
			}
		} catch (PException e) {
			e.printStackTrace();
		} finally {
			t.close();
		}
	}

	protected synchronized void connect() {
		super.connect();
		if (!available())
			return;
		CMPPConnectMessage request = null;
		CMPP30ConnectRepMessage rsp = null;
		try {
			request = new CMPPConnectMessage(source_addr, version, shared_secret, new Date());
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			close();
			setError(CMPPConstant.CONNECT_INPUT_ERROR);
		}
		CMPP30Transaction t = (CMPP30Transaction) createChild();
		try {
			t.send(request);
			PMessage m = super.in.read();
			onReceive(m);
		} catch (IOException e) {
			e.printStackTrace();
			close();
			setError(CMPPConstant.LOGIN_ERROR.concat(explain(e)));
		}
		rsp = (CMPP30ConnectRepMessage) t.getResponse();
		if (rsp == null) {
			close();
			setError(CMPPConstant.CONNECT_TIMEOUT);
		}
		t.close();
		if (rsp != null && rsp.getStatus() != 0) {
			close();
			if (rsp.getStatus() == 1)
				setError(CMPPConstant.STRUCTURE_ERROR);
			else if (rsp.getStatus() == 2)
				setError(CMPPConstant.NONLICETSP_ID);
			else if (rsp.getStatus() == 3)
				setError(CMPPConstant.SP_ERROR);
			else if (rsp.getStatus() == 4)
				setError(CMPPConstant.VERSION_ERROR);
			else
				setError(CMPPConstant.OTHER_ERROR);
		}
		notifyAll();
	}
}