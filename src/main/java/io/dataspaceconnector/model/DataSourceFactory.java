package io.dataspaceconnector.model;

import org.springframework.stereotype.Component;

/**
 * Creates and updates data sources
 */
@Component
public class DataSourceFactory implements AbstractFactory<DataSource, DataSourceDesc> {

    private static final String DEFAULT_PATH = "default";

    /**
     * @param desc The description of the entity.
     * @return The new data source factory.
     */
    @Override
    public DataSource create(DataSourceDesc desc) {
        return null;
    }

    /**
     * @param dataSource The data source entity.
     * @param desc       The description of the new entity.
     * @return True, if data source is updated.
     */
    @Override
    public boolean update(DataSource entity, DataSourceDesc desc) {
        return false;
    }
}
