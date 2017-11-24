package info.globalbus.oraclewrapper.example;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = Application.class)
public class DBTest {
    @Autowired
    ExampleProcDao exampleProcDao;
    @Autowired
    TestDao testDao;

    @Test
    public void exampleTest() throws Exception {
        Complex obj = new Complex();
        obj.setRPart(5.0);
        obj.setIPart(6.0);
        List<Complex> test = exampleProcDao.getList(obj);
        Assert.assertTrue(test.size() == 2);
    }

    @Test
    public void test() throws Exception {
        Complex obj = new Complex();
        obj.setRPart(5.0);
        obj.setIPart(6.0);
        Response test = testDao.get(obj);
        Assert.assertTrue(test.message != null);
    }

    @Test
    public void multithread() throws Exception {
        Complex obj = new Complex();
        obj.setRPart(5.0);
        obj.setIPart(6.0);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        for (int i=0; i<100000;i++) {
            pool.submit(()-> testDao.get(obj));
        }
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.MINUTES);
        pool.isTerminated();
    }
}