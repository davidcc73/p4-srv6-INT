package org.onosproject.ecmp;

import org.onosproject.net.ElementId;
import org.onosproject.net.Path;

import java.util.Set;

/**
 * Created by cr on 16-11-29.
 */
public interface  ECMPPathService {
    Path getPath(ElementId srcID, ElementId dstID, int flowLabel);
}