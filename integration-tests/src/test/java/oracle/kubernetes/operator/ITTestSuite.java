package oracle.kubernetes.operator;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  ITSingleDomain.class,
  ITSecondDomain.class,
  ITThirdDomain.class,
  ITFourthDomain.class
})
public class ITTestSuite {}
