package org.abimon.umbra;

import java.util.Objects;

public class GradleDependency {
    public String group;
    public String module;
    public String version;
    public String classFile;
    public String qualifier;

    public GradleDependency(String group, String module, String version, String classFile) {
        this.group = group;
        this.module = module;
        this.version = version;
        this.classFile = classFile;

        this.qualifier = group + '_' + module + '_' + version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GradleDependency)) return false;
        GradleDependency that = (GradleDependency) o;
        return Objects.equals(group, that.group) &&
                Objects.equals(module, that.module) &&
                Objects.equals(version, that.version) &&
                Objects.equals(classFile, that.classFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, module, version, classFile);
    }
}
