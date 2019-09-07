package org.microemu.android;

public abstract class AndroidRuntimeMethods {

    private static Impl instance;

    private static Impl getInstance() {
        if (instance == null) {
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                instance = new Impl21();
            } else if (android.os.Build.VERSION.SDK_INT >= 14) {
                instance = new Impl14();
            } else {
                instance = new Impl();
            }
        }
        return instance;
    }

    private AndroidRuntimeMethods() {
    }

    public static boolean hasPermanentMenuKey(android.content.Context context) {
        return getInstance().hasPermanentMenuKey(context);
    }
    
    public static void invalidateOptionsMenu(android.app.Activity activity) {
        getInstance().invalidateOptionsMenu(activity);
    }

    public static void setShowAsAction(android.view.MenuItem item) {
        getInstance().setShowAsAction(item);
    }

    public static Object getActionBar(android.app.Activity activity) {
        return getInstance().getActionBar(activity);
    }

    public static void setActionBar(android.app.Activity activity, android.view.View toolbarView) {
        getInstance().setActionBar(activity, toolbarView);
    }

    public static void setActionBarVisibility(android.app.Activity activity, Object actionBar, int visibility) {
        getInstance().setActionBarVisibility(activity, actionBar, visibility);
    }

    public static void setSubtitle(Object view, CharSequence text) {
        getInstance().setSubtitle(view, text);
    }

    static class Impl {

        Impl() {
        }

        public boolean hasPermanentMenuKey(android.content.Context context) {
            return true;
        }

        public void invalidateOptionsMenu(android.app.Activity activity) {
            throw new UnsupportedOperationException("invalidateOptionsMenu");
        }

        public void setShowAsAction(android.view.MenuItem item) {
            throw new UnsupportedOperationException("setShowAsAction");
        }

        public Object getActionBar(android.app.Activity activity) {
            throw new UnsupportedOperationException("getActionBar");
        }

        public void setActionBar(android.app.Activity activity, android.view.View toolbarView) {
            throw new UnsupportedOperationException("setActionBar");
        }

        public void setActionBarVisibility(android.app.Activity activity, Object actionBar, int visibility) {
            throw new UnsupportedOperationException("setActionBarVisibility");
        }

        public void setSubtitle(Object view, CharSequence text) {
            throw new UnsupportedOperationException("setSubtitle");
        }
    }

    static class Impl14 extends Impl {
    
        Impl14() {
        }

        @Override
        public boolean hasPermanentMenuKey(android.content.Context context) {
            return android.view.ViewConfiguration.get(context).hasPermanentMenuKey();
        }

        @Override
        public void invalidateOptionsMenu(android.app.Activity activity) {
            activity.invalidateOptionsMenu();
        }

        @Override
        public void setShowAsAction(android.view.MenuItem item) {
            item.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        }

        @Override
        public Object getActionBar(android.app.Activity activity) {
            return activity.getActionBar();
        }

        @Override
        public void setActionBarVisibility(android.app.Activity activity, Object actionBar, int visibility) {
            setActionBarViewVisibility(activity, visibility); // this will make it skip transition
            if (true)
                return;
                
            if (visibility == android.view.View.VISIBLE) {
                ((android.app.ActionBar) actionBar).show();
            } else if (visibility == android.view.View.INVISIBLE) {
                ((android.app.ActionBar) actionBar).hide();
            } else {
                throw new IllegalArgumentException("visibility = " + visibility);
            }
        }

        @Override
        public void setSubtitle(Object view, CharSequence text) {
            ((android.app.ActionBar) view).setSubtitle(text);
        }

        private void setActionBarViewVisibility(android.app.Activity activity, int visibility) {
            int resId = android.content.res.Resources.getSystem().getIdentifier("action_bar_container", "id", "android");
            if (resId != 0) {
                activity.getWindow().getDecorView().findViewById(resId).setVisibility(visibility);
            }
        }
    }

    static class Impl21 extends Impl14 {

        Impl21() {
        }

        @Override
        public Object getActionBar(android.app.Activity activity) {
            throw new UnsupportedOperationException("getActionBar");
        }

        @Override
        public void setActionBar(android.app.Activity activity, android.view.View toolbarView) {
            if (toolbarView == null) {
                try {
                    activity.setActionBar((android.widget.Toolbar) null);
                } catch (NullPointerException e) { // fixed in later 6.0 similarly to the bellow code
                    try {
                        java.lang.reflect.Field field = android.app.Activity.class.getDeclaredField("mActionBar");
                        System.out.println("field " + field);
                        field.setAccessible(true);
                        field.set(activity, null);
                        activity.getWindow().setCallback(activity);
                        activity.invalidateOptionsMenu();
                        ((org.microemu.android.MicroEmulatorActivity) activity).mActionBarField = true; 
                    } catch (Exception r) {
                        System.out.println("field mActionBar does not exist or not accessible");
                    }                
                }
            } else {
                activity.setActionBar((android.widget.Toolbar) toolbarView);
            }
        }

        @Override
        public void setActionBarVisibility(android.app.Activity activity, Object actionBar, int visibility) {
            throw new UnsupportedOperationException("setActionBarVisibility");
        }

        @Override
        public void setSubtitle(Object view, CharSequence text) {
            ((android.widget.Toolbar) view).setSubtitle(text);
        }
    }
}
