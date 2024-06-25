package libs;

public class Pair<T, E> {
    private T first;
    private E second;

    public Pair(T t, E e) {
        first = t;
        second = e;
    }

    public T getFirst() {
        return first;
    }

    public E getSecond() {
        return second;
    }

    public void setFirst(T t) {
        first = t;
    }

    public void setSecond(E e) {
        second = e;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        else if (o.getClass() != this.getClass()) return false;
        else {
            Pair<T, E> temp = (Pair<T, E>) o;
            return temp.getFirst().equals(this.getFirst()) && temp.getSecond().equals(this.getSecond());
        }
    }
}
