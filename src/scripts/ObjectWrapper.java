package scripts;

import java.lang.reflect.Type;

public class ObjectWrapper {

    public Type type;
    public Object object;

    public ObjectWrapper() {
    }

    public ObjectWrapper(Type t, Object o) {
        type = t;
        object = o;
    }

    @Override
    public String toString() {
        String result = null;
        if (type == String.class) {
            result = ((String) object);
        } else if (type == Float.class) {
            result = ((Float) object).toString();
        } else if (type == Boolean.class) {
            result = ((Boolean) object).toString();
        }
        return result;
    }

    /**
     * Sets the value of this to the value and type of the given object
     *
     * @param obj The object to use
     */
    public void set(Object obj) {
        if (obj != null) {
            object = obj;
            if (object instanceof String) {
                type = String.class;
            } else if (object instanceof Float) {
                type = Float.class;
            } else if (object instanceof Boolean) {
                type = Boolean.class;
            }
        }
    }
}
