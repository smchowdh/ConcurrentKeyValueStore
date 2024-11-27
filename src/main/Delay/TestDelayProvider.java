package main.Delay;

public class TestDelayProvider implements DelayProvider{

    @Override
    public void delay() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
