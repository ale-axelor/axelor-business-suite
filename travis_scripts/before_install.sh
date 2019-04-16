#!/bin/bash
mkdir abs
mv * abs
git clone https://github.com/axelor/abs-webapp.git
mv abs abs-webapp/modules
mv abs-webapp/* .
rm -rf abs-webapp
