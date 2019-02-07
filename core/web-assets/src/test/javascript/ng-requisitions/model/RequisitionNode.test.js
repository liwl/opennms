/*global RequisitionNode:true */

/**
* @author Alejandro Galue <agalue@opennms.org>
* @copyright 2014 The OpenNMS Group, Inc.
*/

'use strict';

const angular = require('angular-js');
const RequisitionNode = require('../../../../../src/main/assets/js/apps/onms-requisitions/lib/scripts/model/RequisitionNode');

var onmsNode = {
  'foreign-id': '1001',
  'node-label': 'testing-node',
  'location' : 'moo',
  'building' : 'Office',
  'parent-foreign-source' : 'routers',
  'parent-node-label' : 'rt001.local',
  'parent-foreign-id' : null,
  'interface': [{
    'ip-addr': '10.0.0.1',
    'descr': 'eth0',
    'snmp-primary': 'P',
    'status': '1',
    'monitored-service': [{
      'service-name': 'ICMP'
    },{
      'service-name': 'SNMP'
    }]
  }],
  'asset': [{
    'name': 'address1',
    'value': '220 Chatham Business Drive'
  },{
    'name': 'city',
    'value': 'Pittsboro'
  }],
  'category': [{
    'name': 'Servers'
  }],
  'meta-data': [
    {'context': 'requisition', 'key': 'foo', 'value': 'bar'},
    {'context': 'requisition', 'key': 'bar', 'value': 'foo'},
    {'context': 'external1', 'key': 'kickit', 'value': 'mumumu'},
    {'context': 'external1', 'key': 'lickit', 'value': 'mimimi'},
    {'context': 'external2', 'key': 'first', 'value': 'momomo'},
    {'context': 'external2', 'key': 'second', 'value': 'mamama'},
  ]
};

test('Model: RequisitionsNode: verify object translation', function () {
  var reqNode = new RequisitionNode('test-requisition', onmsNode, false);
  expect(reqNode).not.toBe(null);
  expect(reqNode.parentForeignSource).toBe('routers');
  expect(reqNode.parentForeignId).toBe(null);
  expect(reqNode.parentNodeLabel).toBe('rt001.local');
  expect(reqNode.location).toBe('moo');
  expect(reqNode.categories.length).toBe(1);
  expect(reqNode.categories[0].name).toBe('Servers');
  expect(reqNode.interfaces.length).toBe(1);
  expect(reqNode.interfaces[0].ipAddress).toBe('10.0.0.1');
  expect(reqNode.interfaces[0].services.length).toBe(2);
  expect(reqNode.interfaces[0].services[0].name).toBe('ICMP');
  expect(reqNode.assets[1].value).toBe('Pittsboro');
  expect(reqNode.requisitionMetaData[0].key).toBe('foo');
  expect(reqNode.requisitionMetaData[0].value).toBe('bar');
  expect(reqNode.requisitionMetaData[1].key).toBe('bar');
  expect(reqNode.requisitionMetaData[1].value).toBe('foo');
  expect(reqNode.otherMetaData['external1'][0].key).toBe('kickit');
  expect(reqNode.otherMetaData['external1'][0].value).toBe('mumumu');
  expect(reqNode.otherMetaData['external1'][1].key).toBe('lickit');
  expect(reqNode.otherMetaData['external1'][1].value).toBe('mimimi');
  expect(reqNode.otherMetaData['external2'][0].key).toBe('first');
  expect(reqNode.otherMetaData['external2'][0].value).toBe('momomo');
  expect(reqNode.otherMetaData['external2'][1].key).toBe('second');
  expect(reqNode.otherMetaData['external2'][1].value).toBe('mamama');

  var genNode = reqNode.getOnmsRequisitionNode();
  expect(genNode).not.toBe(null);
  expect(angular.equals(genNode, onmsNode)).toBe(true);
});
