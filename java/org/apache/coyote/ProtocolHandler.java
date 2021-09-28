/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote;

import java.util.concurrent.Executor;

import org.apache.tomcat.util.net.SSLHostConfig;

/**
 * Abstract the protocol implementation, including threading, etc.
 *
 * This is the main interface to be implemented by a coyote protocol.
 * Adapter is the main interface to be implemented by a coyote servlet
 * container.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 * @see Adapter
 */
public interface ProtocolHandler {
    //要理解Connector，我们需要问自己4个问题。
    //
    //（1）Connector如何接受请求的？
    //（2）如何将请求封装成Request和Response的？
    //（3）封装完之后的Request和Response如何交给Container进行处理的？
    //（4）Container处理完之后如何交给Connector并返回给客户端的？
    //
    // Connector的启动方法就是ProtocolHandler.start()

    //ProtocolHandler包含三个部件：Endpoint、Processor、Adapter。
    //
    //1.Endpoint用来处理底层Socket的网络连接，Processor用于将Endpoint接收到的Socket封装成Request，
    // Adapter用于将Request交给Container进行具体的处理。
    //
    //2.Endpoint由于是处理底层的Socket网络连接，因此Endpoint是用来实现TCP/IP协议的，而Processor用来实现HTTP协议的，
    // Adapter将请求适配到Servlet容器进行具体的处理。
    //
    //3.Endpoint的抽象实现类AbstractEndpoint里面定义了Acceptor和AsyncTimeout两个内部类和一个Handler接口。
    // Acceptor用于监听请求，AsyncTimeout用于检查异步Request的超时，Handler用于处理接收到的Socket，在内部调用Processor进行处理。

    /**
     * Return the adapter associated with the protocol handler.
     * @return the adapter
     */
    public Adapter getAdapter();


    /**
     * The adapter, used to call the connector.
     *
     * @param adapter The adapter to associate
     */
    public void setAdapter(Adapter adapter);


    /**
     * The executor, provide access to the underlying thread pool.
     *
     * @return The executor used to process requests
     */
    public Executor getExecutor();


    /**
     * Initialise the protocol.
     *
     * @throws Exception If the protocol handler fails to initialise
     */
    public void init() throws Exception;


    /**
     * Start the protocol.
     *
     * @throws Exception If the protocol handler fails to start
     */
    public void start() throws Exception;


    /**
     * Pause the protocol (optional).
     *
     * @throws Exception If the protocol handler fails to pause
     */
    public void pause() throws Exception;


    /**
     * Resume the protocol (optional).
     *
     * @throws Exception If the protocol handler fails to resume
     */
    public void resume() throws Exception;


    /**
     * Stop the protocol.
     *
     * @throws Exception If the protocol handler fails to stop
     */
    public void stop() throws Exception;


    /**
     * Destroy the protocol (optional).
     *
     * @throws Exception If the protocol handler fails to destroy
     */
    public void destroy() throws Exception;


    /**
     * Close the server socket (to prevent further connections) if the server
     * socket was bound on {@link #start()} (rather than on {@link #init()}
     * but do not perform any further shutdown.
     */
    public void closeServerSocketGraceful();


    /**
     * Requires APR/native library
     *
     * @return <code>true</code> if this Protocol Handler requires the
     *         APR/native library, otherwise <code>false</code>
     */
    public boolean isAprRequired();


    /**
     * Does this ProtocolHandler support sendfile?
     *
     * @return <code>true</code> if this Protocol Handler supports sendfile,
     *         otherwise <code>false</code>
     */
    public boolean isSendfileSupported();


    public void addSslHostConfig(SSLHostConfig sslHostConfig);
    public SSLHostConfig[] findSslHostConfigs();


    public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol);
    public UpgradeProtocol[] findUpgradeProtocols();
}
