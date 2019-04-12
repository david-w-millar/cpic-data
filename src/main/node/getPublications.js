const axios = require('axios');
const cpicapi = require('./cpicapi');
const fs = require('fs');

const defaultFilename = 'publications.json';

exports.getPublications = (path) => {
  const uri = cpicapi.apiUrl('/guideline');
  const filePath = `${path}/${defaultFilename}`;

  axios.get(
    uri,
    {params: {select: 'id,name,url,publication(*)', order: 'name'}},
  )
    .then((r) => {
      fs.writeFile(filePath, JSON.stringify(r.data, null, 2), (e) => {
        if (e) console.log(e);
        console.log(`Done writing ${filePath}`);
      });
    });
};
