/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.management.ObjectName;

import org.apache.catalina.Contained;
import org.apache.catalina.Container;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Valve;
import org.apache.catalina.util.LifecycleBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * 处理管道的标准实现，它将调用已配置为按顺序调用的一系列Valve。 此实现可用于任何类型的Container。
 * 实施警告-此实施假定当前正在处理请求时，不允许调用addValve（）或removeValve。
 * 否则，将需要修改维护每个线程状态的机制。
 *
 *
 * Standard implementation of a processing <b>Pipeline</b> that will invoke
 * a series of Valves that have been configured to be called in order.  This
 * implementation can be used for any type of Container.
 *
 * <b>IMPLEMENTATION WARNING</b> - This implementation assumes that no
 * calls to <code>addValve()</code> or <code>removeValve</code> are allowed
 * while a request is currently being processed.  Otherwise, the mechanism
 * by which per-thread state is maintained will need to be modified.
 *
 *
 * 在往 Pipeline 中添加 Valve 阀门时，是添加到 first 后面，basic 前面
 *
 * @author Craig R. McClanahan
 */
public class StandardPipeline extends LifecycleBase
        implements Pipeline, Contained {

    private static final Log log = LogFactory.getLog(StandardPipeline.class);
    private static final StringManager sm = StringManager.getManager(Constants.Package);

    // ----------------------------------------------------------- Constructors


    /**
     * 构造一个没有所属容器的管道
     * Construct a new StandardPipeline instance with no associated Container.
     */
    public StandardPipeline() {

        this(null);

    }


    /**
     * 构造一个有所属容器的管道
     * Construct a new StandardPipeline instance that is associated with the
     * specified Container.
     *
     * @param container The container we should be associated with
     */
    public StandardPipeline(Container container) {

        super();
        setContainer(container);

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 基本阀门，最后执行的阀门
     * The basic Valve (if any) associated with this Pipeline.
     */
    protected Valve basic = null;


    /**
     * 管道所属的容器
     * The Container with which this Pipeline is associated.
     */
    protected Container container = null;


    /**
     *  管道里面的首个执行的阀门
     * The first valve associated with this Pipeline.
     */
    protected Valve first = null;


    // --------------------------------------------------------- Public Methods

    @Override //是否异步执行，如果一个阀门都没有，或者所有阀门都是异步执行的，才返回true
    public boolean isAsyncSupported() {
        Valve valve = (first!=null)?first:basic;
        boolean supported = true;
        while (supported && valve!=null) {
            supported = supported & valve.isAsyncSupported();
            valve = valve.getNext();
        }
        return supported;
    }


    @Override //查找所有未异步执行的阀门
    public void findNonAsyncValves(Set<String> result) {
        Valve valve = (first!=null) ? first : basic;
        while (valve != null) {
            if (!valve.isAsyncSupported()) {
                result.add(valve.getClass().getName());
            }
            valve = valve.getNext();
        }
    }


    // ------------------------------------------------------ Contained Methods

    /**
     * 获取所属容器
     * Return the Container with which this Pipeline is associated.
     */
    @Override
    public Container getContainer() {
        return this.container;
    }


    /**
     * 设置所属容器
     * Set the Container with which this Pipeline is associated.
     *
     * @param container The new associated container
     */
    @Override
    public void setContainer(Container container) {
        this.container = container;
    }


    @Override //初始化逻辑，默认没有任何逻辑
    protected void initInternal() {
        // NOOP
    }


    /**
     * 开始逻辑，调用所有阀门的start方法
     * Start {@link Valve}s) in this pipeline and implement the requirements
     * of {@link LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {
        //它也是一个Lifecycle。在容器启动的时候，StandardPipeline 会遍历 Valve 链表，
        // 如果 Valve 是 Lifecycle 的子类，则会调用其 start 方法启动 Valve 组件

        // Start the Valves in our pipeline (including the basic), if any
        Valve current = first;
        if (current == null) {
            current = basic;
        }
        while (current != null) {
            if (current instanceof Lifecycle)
                ((Lifecycle) current).start();
            current = current.getNext();
        }

        setState(LifecycleState.STARTING);
    }


    /**
     * 停止逻辑，调用所有阀门的stop方法
     *
     * Stop {@link Valve}s) in this pipeline and implement the requirements
     * of {@link LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);

        // Stop the Valves in our pipeline (including the basic), if any
        Valve current = first;
        if (current == null) {
            current = basic;
        }
        while (current != null) {
            if (current instanceof Lifecycle)
                ((Lifecycle) current).stop();
            current = current.getNext();
        }
    }


    @Override //销毁逻辑，移掉所有阀门，调用removeValve方法
    protected void destroyInternal() {
        Valve[] valves = getValves();
        for (Valve valve : valves) {
            removeValve(valve);
        }
    }


    /**
     * Return a String representation of this component.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Pipeline[");
        sb.append(container);
        sb.append(']');
        return sb.toString();
    }


    // ------------------------------------------------------- Pipeline Methods


    /**
     * 获取基础阀门
     * <p>Return the Valve instance that has been distinguished as the basic
     * Valve for this Pipeline (if any).
     */
    @Override
    public Valve getBasic() {
        return this.basic;
    }


    /**
     * 设置基础阀门
     * <p>Set the Valve instance that has been distinguished as the basic
     * Valve for this Pipeline (if any).  Prior to setting the basic Valve,
     * the Valve's <code>setContainer()</code> will be called, if it
     * implements <code>Contained</code>, with the owning Container as an
     * argument.  The method may throw an <code>IllegalArgumentException</code>
     * if this Valve chooses not to be associated with this Container, or
     * <code>IllegalStateException</code> if it is already associated with
     * a different Container.</p>
     *
     * @param valve Valve to be distinguished as the basic Valve
     */
    @Override
    public void setBasic(Valve valve) {

        // Change components if necessary
        Valve oldBasic = this.basic;
        if (oldBasic == valve)
            return;

        // Stop the old component if necessary
        //老的基础阀门会被调用stop方法且所属容器置为null
        if (oldBasic != null) {
            if (getState().isAvailable() && (oldBasic instanceof Lifecycle)) {
                try {
                    ((Lifecycle) oldBasic).stop();
                } catch (LifecycleException e) {
                    log.error(sm.getString("standardPipeline.basic.stop"), e);
                }
            }
            if (oldBasic instanceof Contained) {
                try {
                    ((Contained) oldBasic).setContainer(null);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                }
            }
        }

        // Start the new component if necessary
        //新的阀门会设置所属容器，并调用start方法
        if (valve == null)
            return;
        if (valve instanceof Contained) {
            ((Contained) valve).setContainer(this.container);
        }
        if (getState().isAvailable() && valve instanceof Lifecycle) {
            try {
                ((Lifecycle) valve).start();
            } catch (LifecycleException e) {
                log.error(sm.getString("standardPipeline.basic.start"), e);
                return;
            }
        }

        // Update the pipeline
        //替换pipeline中的基础阀门，就是讲基础阀门的前一个阀门的next指向当前阀门
        Valve current = first;
        while (current != null) {
            if (current.getNext() == oldBasic) {
                current.setNext(valve);
                break;
            }
            current = current.getNext();
        }

        this.basic = valve;

    }


    /**
     * <p>Add a new Valve to the end of the pipeline associated with this
     * Container.  Prior to adding the Valve, the Valve's
     * <code>setContainer()</code> method will be called, if it implements
     * <code>Contained</code>, with the owning Container as an argument.
     * The method may throw an
     * <code>IllegalArgumentException</code> if this Valve chooses not to
     * be associated with this Container, or <code>IllegalStateException</code>
     * if it is already associated with a different Container.</p>
     *
     * @param valve Valve to be added
     *
     * @exception IllegalArgumentException if this Container refused to
     *  accept the specified Valve
     * @exception IllegalArgumentException if the specified Valve refuses to be
     *  associated with this Container
     * @exception IllegalStateException if the specified Valve is already
     *  associated with a different Container
     */
    @Override
    public void addValve(Valve valve) { // 添加阀门

        // Validate that we can add this Valve //设置所属容器
        if (valve instanceof Contained)
            ((Contained) valve).setContainer(this.container);

        // Start the new component if necessary //调用阀门的start方法
        if (getState().isAvailable()) {
            if (valve instanceof Lifecycle) {
                try {
                    ((Lifecycle) valve).start();
                } catch (LifecycleException e) {
                    log.error(sm.getString("standardPipeline.valve.start"), e);
                }
            }
        }

        // Add this Valve to the set associated with this Pipeline  //设置阀门，将阀门添加到基础阀门的前一个
        if (first == null) {
            first = valve;
            valve.setNext(basic);
        } else {
            Valve current = first;
            while (current != null) {
                if (current.getNext() == basic) {
                    current.setNext(valve);
                    valve.setNext(basic);
                    break;
                }
                current = current.getNext();
            }
        }

        container.fireContainerEvent(Container.ADD_VALVE_EVENT, valve);
    }


    /**
     * 获取阀门数组
     * Return the set of Valves in the pipeline associated with this
     * Container, including the basic Valve (if any).  If there are no
     * such Valves, a zero-length array is returned.
     */
    @Override
    public Valve[] getValves() {

        List<Valve> valveList = new ArrayList<>();
        Valve current = first;
        if (current == null) {
            current = basic;
        }
        while (current != null) {
            valveList.add(current);
            current = current.getNext();
        }

        return valveList.toArray(new Valve[0]);

    }

    //JMX方法，在此忽略
    public ObjectName[] getValveObjectNames() {

        List<ObjectName> valveList = new ArrayList<>();
        Valve current = first;
        if (current == null) {
            current = basic;
        }
        while (current != null) {
            if (current instanceof JmxEnabled) {
                valveList.add(((JmxEnabled) current).getObjectName());
            }
            current = current.getNext();
        }

        return valveList.toArray(new ObjectName[0]);

    }

    /**
     * Remove the specified Valve from the pipeline associated with this
     * Container, if it is found; otherwise, do nothing.  If the Valve is
     * found and removed, the Valve's <code>setContainer(null)</code> method
     * will be called if it implements <code>Contained</code>.
     *
     * @param valve Valve to be removed
     */
    @Override
    public void removeValve(Valve valve) { //移除阀门

        Valve current;
        if(first == valve) {
            //如果待移出的阀门是首个阀门，则首个阀门的下一个阀门变成首个阀门
            first = first.getNext();
            current = null;
        } else {
            current = first;
        }
        //遍历阀门集合，并进行移除
        while (current != null) {
            if (current.getNext() == valve) {
                current.setNext(valve.getNext());
                break;
            }
            current = current.getNext();
        }

        if (first == basic) first = null;

        //设置阀门所属容器为null
        if (valve instanceof Contained)
            ((Contained) valve).setContainer(null);

        //调用待移除阀门的stop方法和destroy方法，并触发移除阀门事件
        if (valve instanceof Lifecycle) {
            // Stop this valve if necessary
            if (getState().isAvailable()) {
                try {
                    ((Lifecycle) valve).stop();
                } catch (LifecycleException e) {
                    log.error(sm.getString("standardPipeline.valve.stop"), e);
                }
            }
            try {
                ((Lifecycle) valve).destroy();
            } catch (LifecycleException e) {
                log.error(sm.getString("standardPipeline.valve.destroy"), e);
            }
        }

        container.fireContainerEvent(Container.REMOVE_VALVE_EVENT, valve);
    }


    @Override  //获取首个阀门，如果阀门列表为null，返回基础阀门
    public Valve getFirst() {
        if (first != null) {
            return first;
        }

        return basic;
    }
}
