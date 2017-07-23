package info.globalbus.oraclewrapper.example;

import java.util.List;
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

    @Test
    public void test() throws Exception {
        Complex obj = new Complex();
        obj.setRPart(5.0);
        obj.setIPart(6.0);
        List<Complex> test = exampleProcDao.getList(obj);
        Assert.assertTrue(test.size() == 2);
    }
}