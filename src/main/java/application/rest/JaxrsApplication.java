/*
 * Copyright 2019 IBM Corporation
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

package application.rest;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import org.eclipse.microprofile.openapi.annotations.ExternalDocumentation;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@OpenAPIDefinition(
        info = @Info(
                title = "kAppNav API",
                version = "1.0",
                description = "kAppNav API for Kubernetes"
                ),
        externalDocs = @ExternalDocumentation(
                url = "https://github.com/kappnav/apis", 
                description="Find out more about kAppNav"),
        tags = {
                @Tag(name = "actions", description="kAppNav Actions API"),
                @Tag(name = "applications", description="kAppNav Applications API"),
                @Tag(name = "application", description="kAppNav Application CRUD API"),
                @Tag(name = "components", description="kAppNav Components API"),
                @Tag(name = "configmap", description="kAppNav ConfigMap CRUD API"),
                @Tag(name = "health", description="Health Check for kAppNav API"),
                @Tag(name = "namespaces", description="Kubernetes Namespace List"),
                @Tag(name = "resources", description="kAppNav Resources Map"),
                @Tag(name = "secret", description="kAppNav Secret CRUD API"),
                @Tag(name = "secrets", description="kAppNav Secrets API"),
                @Tag(name = "status", description="kAppNav Status API")
        }
        )
@ApplicationPath("/")
public class JaxrsApplication extends Application {

}
