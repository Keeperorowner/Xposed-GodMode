package com.kaisar.xposed.godmode.service;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import com.kaisar.xposed.godmode.IGodModeManager;
import com.kaisar.xposed.godmode.IObserver;
import com.kaisar.xposed.godmode.rule.ActRules;
import com.kaisar.xposed.godmode.rule.AppRules;
import com.kaisar.xposed.godmode.rule.ViewRule;

public class RemoteGMManager extends IGodModeManager.Stub {

    @Override
    public boolean hasLight() throws RemoteException {
        return false;
    }

    @Override
    public void setEditMode(boolean enable) throws RemoteException {

    }

    @Override
    public boolean isInEditMode() throws RemoteException {
        return false;
    }

    @Override
    public void addObserver(String packageName, IObserver observer) throws RemoteException {

    }

    @Override
    public void removeObserver(String packageName, IObserver observer) throws RemoteException {

    }

    @Override
    public AppRules getAllRules() throws RemoteException {
        return null;
    }

    @Override
    public ActRules getRules(String packageName) throws RemoteException {
        return null;
    }

    @Override
    public boolean writeRule(String packageName, ViewRule viewRule, Bitmap bitmap) throws RemoteException {
        return false;
    }

    @Override
    public boolean updateRule(String packageName, ViewRule viewRule) throws RemoteException {
        return false;
    }

    @Override
    public boolean deleteRule(String packageName, ViewRule viewRule) throws RemoteException {
        return false;
    }

    @Override
    public boolean deleteRules(String packageName) throws RemoteException {
        return false;
    }

    @Override
    public ParcelFileDescriptor openImageFileDescriptor(String filePath) throws RemoteException {
        return null;
    }
}
