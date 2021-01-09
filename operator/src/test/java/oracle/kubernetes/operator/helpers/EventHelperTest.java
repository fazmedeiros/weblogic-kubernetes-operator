// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import oracle.kubernetes.operator.DomainProcessorDelegateStub;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.EventConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainStatusUpdater.createFailureRelatedSteps;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CHANGED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CREATED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_DELETED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_ABORTED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_COMPLETED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_FAILED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_RETRYING_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_STARTING_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_STARTING_PATTERN;
import static oracle.kubernetes.operator.EventTestUtils.containsEvent;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithComponent;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithInstance;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithInvolvedObject;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithLabels;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithMessage;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithNamespace;
import static oracle.kubernetes.operator.EventTestUtils.getEvents;
import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_NAMESPACE_ENV;
import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_POD_NAME_ENV;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_NAME;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CHANGED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CREATED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_DELETED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_ABORTED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_COMPLETED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_FAILED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_RETRYING;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_STARTING;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.NAMESPACE_WATCHING_STARTED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.NAMESPACE_WATCHING_STOPPED;
import static oracle.kubernetes.operator.helpers.EventHelper.createEventStep;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class EventHelperTest {
  private static final String OPERATOR_POD_NAME = "my-weblogic-operator-1234";
  private static final String OP_NS = "operator-namespace";

  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final DomainProcessorDelegateStub processorDelegate = DomainProcessorDelegateStub.createDelegate(testSupport);
  private final DomainProcessorImpl processor = new DomainProcessorImpl(processorDelegate);
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final Map<String, Map<String, DomainPresenceInfo>> presenceInfoMap = new HashMap<>();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final MakeRightDomainOperation makeRightOperation
      = processor.createMakeRightOperation(info);
  private final String jobPodName = LegalNames.toJobIntrospectorName(UID);

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "DOMAINS", presenceInfoMap));
    mementos.add(TuningParametersStub.install());
    mementos.add(HelmAccessStub.install());

    testSupport.addToPacket(JOB_POD_NAME, jobPodName);
    testSupport.addDomainPresenceInfo(info);
    testSupport.defineResources(domain);
    DomainProcessorTestSetup.defineRequiredResources(testSupport);
    HelmAccessStub.defineVariable(OPERATOR_NAMESPACE_ENV, OP_NS);
    HelmAccessStub.defineVariable(OPERATOR_POD_NAME_ENV, OPERATOR_POD_NAME);
  }

  @After
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartingEventCreated() {
    makeRightOperation.execute();

    assertThat("Found DOMAIN_PROCESSING_STARTING event",
        containsEvent(getEvents(testSupport), DOMAIN_PROCESSING_STARTING_EVENT), is(true));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartingEventCreatedWithExpectedLabels() {
    makeRightOperation.execute();

    Map<String, String> expectedLabels = new HashMap();
    expectedLabels.put(LabelConstants.DOMAINUID_LABEL, UID);
    expectedLabels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    assertThat("Found DOMAIN_PROCESSING_STARTING event with expected labels",
        containsEventWithLabels(getEvents(testSupport),
            DOMAIN_PROCESSING_STARTING_EVENT, expectedLabels), is(true));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartingEventCreatedWithExpectedNamespace() {
    makeRightOperation.execute();

    assertThat("Found DOMAIN_PROCESSING_STARTING event with expected namespace",
        containsEventWithNamespace(getEvents(testSupport),
            DOMAIN_PROCESSING_STARTING_EVENT, NS), is(true));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartingEventCreatedWithExpectedMessage() {
    makeRightOperation.execute();

    assertThat("Found DOMAIN_PROCESSING_STARTING event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_PROCESSING_STARTING_EVENT,
            String.format(DOMAIN_PROCESSING_STARTING_PATTERN, UID)), is(true));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartingEventCreatedWithInvolvedObject()
      throws Exception {
    makeRightOperation.execute();

    assertThat("Found DOMAIN_PROCESSING_STARTING event with expected involved object",
        containsEventWithInvolvedObject(getEvents(testSupport), DOMAIN_PROCESSING_STARTING_EVENT, UID, NS), is(true));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartingEventCreatedWithReportingComponent()
      throws Exception {
    makeRightOperation.execute();

    assertThat("Found DOMAIN_PROCESSING_STARTING event with expected reporting component",
        containsEventWithComponent(getEvents(testSupport), DOMAIN_PROCESSING_STARTING_EVENT), is(true));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartingEventCreatedWithReportingInstance()
      throws Exception {
    String namespaceFromHelm = NamespaceHelper.getOperatorNamespace();

    testSupport.runSteps(createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)));

    assertThat("Operator namespace is correct",
        namespaceFromHelm, equalTo(OP_NS));

    assertThat("Found DOMAIN_PROCESSING_STARTING event with expected reporting instance",
        containsEventWithInstance(getEvents(testSupport),
            DOMAIN_PROCESSING_STARTING_EVENT, OPERATOR_POD_NAME), is(true));
  }

  @Test
  public void whenCreateEventStepCalled_domainProcessingCompletedEventCreated() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED))));

    assertThat("Found DOMAIN_PROCESSING_COMPLETED event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_PROCESSING_COMPLETED_EVENT), is(true));
  }

  @Test
  public void whenCreateEventStepCalled_domainProcessingCompletedEventCreatedWithExpectedMessage() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED)))
    );

    assertThat("Found DOMAIN_PROCESSING_COMPLETED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_PROCESSING_COMPLETED_EVENT,
            String.format(DOMAIN_PROCESSING_COMPLETED_PATTERN, UID)), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithOutStartedEvent_domainProcessingCompletedEventNotCreated() {
    testSupport.runSteps(createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED)));

    assertThat("Found DOMAIN_PROCESSING_COMPLETED event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_PROCESSING_COMPLETED_EVENT), is(false));
  }

  @Test
  public void whenCreateEventStepCalledWithRetryingAndEvent_domainProcessingCompletedEventCreated() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_RETRYING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED)))
    );

    assertThat("Found DOMAIN_PROCESSING_COMPLETED event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_PROCESSING_COMPLETED_EVENT), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithFailedEvent_domainProcessingFailedEventCreated() {
    testSupport.runSteps(createFailureRelatedSteps("FAILED", "Test failure", new TerminalStep()));

    assertThat("Found DOMAIN_PROCESSING_FAILED event",
        containsEvent(getEvents(testSupport), DOMAIN_PROCESSING_FAILED_EVENT), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithFailedEvent_domainProcessingFailedEventCreatedWithExpectedMessage() {
    testSupport.runSteps(createFailureRelatedSteps("FAILED", "Test this failure", new TerminalStep()));

    assertThat("Found DOMAIN_PROCESSING_FAILED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_PROCESSING_FAILED_EVENT,
            String.format(DOMAIN_PROCESSING_FAILED_PATTERN, UID, "Test this failure")), is(true));
  }

  @Test
  public void whenMakeRightCalled_withRetryingEventData_domainProcessingRetryingEventCreated() {
    makeRightOperation.withEventData(DOMAIN_PROCESSING_RETRYING, null).execute();

    assertThat("Found DOMAIN_PROCESSING_RETRYING event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_PROCESSING_RETRYING_EVENT), is(true));
  }

  @Test
  public void whenMakeRightCalled_withRetryingEventData_domainProcessingRetryingEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(DOMAIN_PROCESSING_RETRYING, null).execute();

    assertThat("Found DOMAIN_PROCESSING_RETRYING event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_PROCESSING_RETRYING_EVENT,
            String.format(DOMAIN_PROCESSING_RETRYING_PATTERN, UID)), is(true));
  }

  @Test
  public void whenMakeRightCalled_withCreatedEventData_domainCreatedEventCreated() {
    makeRightOperation.withEventData(DOMAIN_CREATED, null).execute();

    assertThat("Found DOMAIN_CREATED event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_CREATED_EVENT), is(true));
  }

  @Test
  public void whenMakeRightCalled_withCreatedEventData_domainCreatedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(DOMAIN_CREATED, null).execute();

    assertThat("Found DOMAIN_CREATED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_CREATED_EVENT,
            String.format(DOMAIN_CREATED_PATTERN, UID)), is(true));
  }

  @Test
  public void whenMakeRightCalled_withChangedEventData_domainChangedEventCreated() {
    makeRightOperation.withEventData(DOMAIN_CHANGED, null).execute();

    assertThat("Found DOMAIN_CHANGED event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_CHANGED_EVENT), is(true));
  }

  @Test
  public void whenMakeRightCalled_withChangedEventData_domainChangedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(DOMAIN_CHANGED, null).execute();

    assertThat("Found DOMAIN_CHANGED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_CHANGED_EVENT,
            String.format(DOMAIN_CHANGED_PATTERN, UID)), is(true));
  }

  @Test
  public void whenMakeRightCalled_withDeletedEventData_domainDeletedEventCreated() {
    makeRightOperation.withEventData(DOMAIN_DELETED, null).execute();

    assertThat("Found DOMAIN_DELETED event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_DELETED_EVENT), is(true));
  }

  @Test
  public void whenMakeRightCalled_withDeletedEventData_domainDeletedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(DOMAIN_DELETED, null).execute();

    assertThat("Found DOMAIN_DELETED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_DELETED_EVENT,
            String.format(DOMAIN_DELETED_PATTERN, UID)), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithAbortedEvent_domainProcessingAbortedEventCreated() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_FAILED)),
        createEventStep(new EventData(DOMAIN_PROCESSING_ABORTED).message("Test this failure")))
    );

    assertThat("Found DOMAIN_PROCESSING_ABORTED event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_PROCESSING_ABORTED_EVENT), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithAbortedEvent_domainProcessingAbortedEventCreatedWithExpectedMessage() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_FAILED)),
        createEventStep(new EventData(DOMAIN_PROCESSING_ABORTED).message("Test this failure")))
    );

    assertThat("Found DOMAIN_PROCESSING_ABORTED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_PROCESSING_ABORTED_EVENT,
            String.format(DOMAIN_PROCESSING_ABORTED_PATTERN, UID, "Test this failure")), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithNSWatchStartedEvent_eventCreatedWithExpectedMessage() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));
    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.NAMESPACE_WATCHING_STARTED_EVENT,
            String.format(EventConstants.NAMESPACE_WATCHING_STARTED_PATTERN, NS)), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithNSWatchStartedEvent_eventCreatedWithExpectedNamespace() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));
    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected namespace",
        containsEventWithNamespace(getEvents(testSupport),
            EventConstants.NAMESPACE_WATCHING_STARTED_EVENT, NS), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithNSWatchStartedEvent_eventCreatedWithExpectedLabels() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));

    Map<String, String> expectedLabels = new HashMap();
    expectedLabels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected labels",
        containsEventWithLabels(getEvents(testSupport),
            EventConstants.NAMESPACE_WATCHING_STARTED_EVENT, expectedLabels), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithNSWatchStartedEvent_eventCreatedWithExpectedInvolvedObject() {
    testSupport.runSteps(createEventStep(
        new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));

    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected involvedObject",
        containsEventWithInvolvedObject(getEvents(testSupport),
            EventConstants.NAMESPACE_WATCHING_STARTED_EVENT, NS, NS), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithNSWatchStoppedEvent_eventCreatedWithExpectedLabels() {
    testSupport.runSteps(createEventStep(
        new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS)));

    Map<String, String> expectedLabels = new HashMap();
    expectedLabels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected labels",
        containsEventWithLabels(getEvents(testSupport),
            EventConstants.NAMESPACE_WATCHING_STOPPED_EVENT, expectedLabels), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithNSWatchStoppedEvent_eventCreatedWithExpectedInvolvedObject() {
    testSupport.runSteps(createEventStep(
        new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS)));

    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected involvedObject",
        containsEventWithInvolvedObject(getEvents(testSupport),
            EventConstants.NAMESPACE_WATCHING_STOPPED_EVENT, NS, NS), is(true));
  }

}