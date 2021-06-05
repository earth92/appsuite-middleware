/*
 * @copyright Copyright (c) OX Software GmbH, Germany <info@open-xchange.com>
 * @license AGPL-3.0
 *
 * This code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OX App Suite.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>.
 *
 * Any use of the work other than as authorized under this license or copyright law is prohibited.
 *
 */

package com.openexchange.admin.diff.result.writer;

import java.util.LinkedList;
import java.util.List;
import com.openexchange.admin.diff.file.domain.ConfigurationFile;
import com.openexchange.admin.diff.result.DiffResult;
import com.openexchange.admin.diff.result.domain.PropertyDiff;
import com.openexchange.admin.diff.util.ConfigurationFileSearch;
import com.openexchange.admin.diff.util.DiffMatchPatch;
import com.openexchange.admin.diff.util.DiffMatchPatch.Diff;
import com.openexchange.admin.diff.util.DiffMatchPatch.Operation;



/**
 * {@link DiffMatchPatchWriter}
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @since 7.6.1
 */
public class DiffMatchPatchWriter implements DiffWriter {

    /**
     * {@inheritDoc}
     */
    @Override
    public void addOutputToDiffResult(DiffResult diffResult, List<ConfigurationFile> lOriginalFiles, List<ConfigurationFile> lInstalledFiles) {

        for (ConfigurationFile origFile : lOriginalFiles) {
            final String fileName = origFile.getName();

            List<ConfigurationFile> result = new ConfigurationFileSearch().search(lInstalledFiles, fileName);

            if (result.isEmpty()) {
                // Missing in installation, but already tracked in file diff
                continue;
            }

            DiffMatchPatch diffMatchPatch = new DiffMatchPatch();
            LinkedList<Diff> diff_main = diffMatchPatch.diff_main(origFile.getContent(), result.get(0).getContent(), false);
            diffMatchPatch.diff_cleanupSemantic(diff_main);

            if (diff_main.size() > 1) {
                String difference = "";
                for (Diff d : diff_main) {
                    if (d.operation != Operation.EQUAL) {
                        difference = difference.concat("\n" + d.operation + ": " + d.text);
                    }
                }
                diffResult.getChangedProperties().add(new PropertyDiff(origFile.getFileNameWithExtension(), difference, null));
            }
        }
    }
}
