/*
 * Copyright 2020 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package application.rest.v1.configmaps;

/**
 * This class represents a Kubernetes owner reference.
 */
public class OwnerRef {

    private final String apiVersion; 
    private final String kind;
    private final String uid; 

    public OwnerRef(String ownerApiVersion, String ownerKind, String ownerUID) { 
        this.apiVersion= ownerApiVersion; 
        this.kind= ownerKind; 
        this.uid= ownerUID;
    }

    public String getApiVersion() { return apiVersion; }
    public String getKind() { return kind;}
    public String getUID() { return uid;}

    public String toString() { 
        return "{" + this.apiVersion + "," + this.kind + "," + this.uid + "}"; 
    }

    public boolean kindEquals(String kind) { 
        return this.kind.equals(kind); 
    }

    public boolean apiVersionMatches(String apiVersion) { 
        if ( apiVersion.equals("*") ) { 
            return true; 
        }
        else { 
            return this.apiVersion.equals(apiVersion); 
        } 
    }

    public boolean uidMatches(String uid) { 
        if ( uid.equals("*") ) { 
            return true; 
        }
        else { 
            return this.uid.equals(uid); 
        } 
    }

}